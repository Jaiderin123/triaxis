package dev.triaxis.assistant.infrastructure.adapter.out.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.triaxis.assistant.application.port.out.UrgencyViewStore;
import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.assistant.domain.model.UrgencyView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The urgency view in Redis: one hash per patient ({@code urgency:patient:<id>}), one hash of counts per level
 * ({@code urgency:counters}) and a metadata hash ({@code urgency:meta}). Events are applied by a Lua script, so
 * each one is atomic and applying it twice changes nothing (ADR 0021).
 */
@Component
class RedisUrgencyViewStore implements UrgencyViewStore {

	static final String PATIENT_PREFIX = "urgency:patient:";
	static final String COUNTERS = "urgency:counters";
	static final String META = "urgency:meta";

	private final RedisScript<Long> apply;
	private final ReactiveStringRedisTemplate redis;
	private final Duration dischargedTtl;
	private final Duration timeout;

	RedisUrgencyViewStore(ReactiveStringRedisTemplate redis,
			@Value("${assistant.urgency.discharged-ttl:P30D}") Duration dischargedTtl,
			@Value("${assistant.urgency.redis-timeout:PT2S}") Duration timeout) {
		this.redis = redis;
		// Read once at startup: a script built from a resource reads the file again, on a Redis I/O thread, the
		// first time Redis does not know it (BlockHound caught it).
		this.apply = RedisScript.of(readScript("redis/urgency-apply.lua"), Long.class);
		this.dischargedTtl = dischargedTtl;
		this.timeout = timeout;
	}

	@Override
	public Mono<Boolean> apply(UrgencyEvent event) {
		List<String> keys = List.of(PATIENT_PREFIX + event.patientId(), COUNTERS, META);
		List<String> args = List.of(event.kind().name(), event.level().name(),
				String.valueOf(event.occurredAt().toEpochMilli()), String.valueOf(dischargedTtl.toSeconds()));
		return redis.execute(apply, keys, args).next().map(changed -> changed == 1L).timeout(timeout);
	}

	@Override
	public Mono<UrgencyView> read() {
		Mono<Map<TriageLevel, Long>> counts = redis.<String, String>opsForHash().entries(COUNTERS)
				.collectMap(entry -> TriageLevel.valueOf(entry.getKey()), entry -> Long.parseLong(entry.getValue()),
						() -> new EnumMap<>(TriageLevel.class));
		Mono<Instant> newest = redis.<String, String>opsForHash().get(META, "lastEventAt")
				.map(millis -> Instant.ofEpochMilli(Long.parseLong(millis)));
		return counts.zipWith(newest.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
				.map(tuple -> new UrgencyView(tuple.getT1(), tuple.getT2().orElse(null)))
				.timeout(timeout);
	}

	@Override
	public Mono<Boolean> isInitialized() {
		return redis.hasKey(META).timeout(timeout);
	}

	@Override
	public Mono<Void> reset() {
		return redis.scan(ScanOptions.scanOptions().match(PATIENT_PREFIX + "*").count(500).build())
				.buffer(500)
				.concatMap(batch -> redis.delete(batch.toArray(String[]::new)))
				.then(redis.delete(COUNTERS, META))
				.then(redis.<String, String>opsForHash().put(META, "initializedAt",
						String.valueOf(Instant.now().toEpochMilli())))
				.then()
				.timeout(Duration.ofSeconds(30));
	}

	private static String readScript(String path) {
		try (var in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot read the Redis script " + path, e);
		}
	}

}
