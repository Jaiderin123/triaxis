package dev.triaxis.assistant.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the protocol corpus.
 *
 * @param location  folder of the protocol files ({@code *.md})
 * @param retry     back-off of the embedding when the provider is unavailable at startup
 */
@ConfigurationProperties("assistant.corpus")
public record CorpusProperties(@DefaultValue("classpath:/protocols/") String location, @DefaultValue Retry retry) {

	public record Retry(@DefaultValue("PT2S") Duration initialBackoff, @DefaultValue("PT1M") Duration maxBackoff) {
	}

}
