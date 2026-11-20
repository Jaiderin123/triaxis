package dev.triaxis.assistant.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One section of a clinical protocol, the unit that is embedded, retrieved and cited.
 *
 * @param id             stable id, for example {@code PROTO-001#medidas-iniciales}
 * @param protocolId     protocol id as cited in the answer, for example {@code PROTO-001}
 * @param protocolTitle  protocol name
 * @param section        section title as written in the corpus, cited in the answer
 * @param content        section text
 * @param contentHash    SHA-256 of {@link #embeddingText()}, used to skip unchanged sections
 */
public record ProtocolChunk(String id, String protocolId, String protocolTitle, String section, String content,
		String contentHash) {

	/** Creates the chunk and computes the hash of the text that will be embedded. */
	public static ProtocolChunk of(String id, String protocolId, String protocolTitle, String section,
			String content) {
		return new ProtocolChunk(id, protocolId, protocolTitle, section, content,
				sha256(embeddingText(protocolTitle, section, content)));
	}

	/** The protocol and section names are embedded with the text, so a question can match them too. */
	public String embeddingText() {
		return embeddingText(protocolTitle, section, content);
	}

	private static String embeddingText(String protocolTitle, String section, String content) {
		return protocolTitle + ". " + section + ". " + content;
	}

	private static String sha256(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

}
