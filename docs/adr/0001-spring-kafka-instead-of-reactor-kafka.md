# 0001. Spring Kafka instead of Reactor Kafka

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The services are reactive, so the natural choice for Kafka would be Reactor Kafka, which exposes producers and consumers as `Flux` and `Mono`. However, Reactor Kafka was discontinued in May 2025 and no longer receives releases. The project also targets Spring Boot 3.5, whose Kafka auto-configuration is built around Spring for Apache Kafka.

## Decision

- Use **Spring for Apache Kafka** (`spring-kafka`), managed by the Spring Boot BOM, for the producer and, in the assistant service, for the consumer.
- Keep the code reactive around it: the producer call (`KafkaTemplate.send`) can block while it fetches metadata, so it runs on `Schedulers.boundedElastic()`, with an explicit timeout, one send at a time per relay cycle.
- Consumers (assistant, alert notifier) use asynchronous `@KafkaListener` methods that return `Mono` with manual acknowledgment, so offsets are committed only after processing.

## Alternatives considered

- **Reactor Kafka:** the most reactive API, but discontinued; adopting it would leave the project on an unmaintained library.
- **Raw Kafka clients:** no dependency on Spring, but it duplicates configuration, serialization and error handling that Spring Kafka already provides.
- **Spring Cloud Stream:** a higher-level abstraction that hides the details this project wants to show (keys, headers, acknowledgment, ordering).

## Consequences

- The blocking edges are explicit and isolated in the messaging adapters, and BlockHound checks that nothing else blocks.
- Throughput is bounded by the one-at-a-time send in the relay, which is enough for this workload; batching sends is a possible later optimization.
- The versions of the Kafka client and Spring Kafka come from the Spring Boot BOM, so no extra pin is needed.
