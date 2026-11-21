package dev.triaxis.assistant.infrastructure.adapter.out.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.triaxis.assistant.application.port.out.ClockPort;
import dev.triaxis.assistant.application.port.out.UrgencyViewStore;
import dev.triaxis.assistant.domain.exception.UrgencyViewUnavailableException;
import dev.triaxis.assistant.domain.model.TriageLevel;
import dev.triaxis.assistant.domain.model.UrgencyEvent;
import dev.triaxis.assistant.domain.model.UrgencyView;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(RedisUrgencyViewStore.class);

	static final String PATIENT_PREFIX = "urgency:patient:";
	static final String COUNTERS = "urgency:counters";
	static final String META = "urgency:meta";
	/**
	 * Written only by {@link #reset()}, after the view was emptied for a replay. It is a key of its own: the Lua
	 * script writes {@code urgency:meta} with every event, so that key cannot tell a built view from a lost one.
	 */
	static final String INITIALIZED = "urgency:initialized";

	private final RedisScript<Long> apply;
	private final ReactiveStringRedisTemplate redis;
	private final ClockPort clock;
	private final MeterRegistry meters;
	private final Duration resetTimeout;
	private final Duration dischargedTtl;
	private final Duration timeout;

	RedisUrgencyViewStore(ReactiveStringRedisTemplate redis, ClockPort clock, MeterRegistry meters,
			@Value("${assistant.urgency.discharged-ttl:P30D}") Duration dischargedTtl,
			@Value("${assistant.urgency.redis-timeout:PT2S}") Duration timeout,
			@Value("${assistant.urgency.reset-timeout:PT30S}") Duration resetTimeout) {
		this.redis = redis;
		this.clock = clock;
		this.meters = meters;
		this.resetTimeout = resetTimeout;
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
				.collect(() -> new EnumMap<TriageLevel, Long>(TriageLevel.class),
						(map, entry) -> readCount(entry.getKey(), entry.getValue()).ifPresent(count -> map.put(
								TriageLevel.valueOf(entry.getKey()), count)));
		Mono<Instant> newest = redis.<String, String>opsForHash().get(META, "lastEventAt")
				.map(millis -> Instant.ofEpochMilli(Long.parseLong(millis)));
		return counts.zipWith(newest.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
				.map(tuple -> new UrgencyView(tuple.getT1(), tuple.getT2().orElse(null)))
				.timeout(timeout)
				.onErrorMap(error -> !(error instanceof UrgencyViewUnavailableException), UrgencyViewUnavailableException::new);
	}

	@Override
	public Mono<Boolean> isInitialized() {
		return redis.hasKey(INITIALIZED).timeout(timeout);
	}

	@Override
	public Mono<Void> reset() {
		return redis.scan(ScanOptions.scanOptions().match(PATIENT_PREFIX + "*").count(500).build())
				.buffer(500)
				.concatMap(batch -> redis.delete(batch.toArray(String[]::new)))
				.then(redis.delete(COUNTERS, META, INITIALIZED))
				.then(redis.opsForValue().set(INITIALIZED, String.valueOf(clock.now().toEpochMilli())))
				.then()
				.timeout(resetTimeout);
	}

	/**
	 * A count as stored, made safe for the read model: an unknown field is ignored, and a negative count (only
	 * possible if the store was changed outside the script, or by concurrent rebuilds of several replicas, which
	 * ASSUMPTIONS A39 rules out) is shown as zero, logged and counted, instead of failing every read with a 500.
	 */
	private java.util.Optional<Long> readCount(String field, String value) {
		if (java.util.Arrays.stream(TriageLevel.values()).noneMatch(level -> level.name().equals(field))) {
			log.warn("Ignoring the unknown field {} of {}", field, COUNTERS);
			return java.util.Optional.empty();
		}
		long count;
		try {
			count = Long.parseLong(value);
		}
		catch (NumberFormatException invalid) {
			log.warn("Ignoring the count of {} that is not a number", field);
			return java.util.Optional.empty();
		}
		if (count < 0) {
			meters.counter("assistant.urgency.negative.counts", "level", field).increment();
			log.warn("The count of {} is negative ({}); it is shown as 0. Rebuild the view by restarting the "
					+ "assistant with an empty Redis", field, count);
			return java.util.Optional.of(0L);
		}
		return java.util.Optional.of(count);
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
