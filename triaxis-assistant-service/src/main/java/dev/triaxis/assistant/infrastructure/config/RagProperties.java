package dev.triaxis.assistant.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retrieval settings (ADR 0007).
 *
 * @param topK               sections retrieved per question
 * @param maxDistance        cosine distance above which a section is not relevant; depends on the embedding model
 * @param questionMaxLength  longest accepted question (ASSUMPTIONS A34)
 */
@ConfigurationProperties("assistant.rag")
public record RagProperties(@DefaultValue("4") int topK, @DefaultValue("0.70") double maxDistance,
		@DefaultValue("1000") int questionMaxLength) {
}
