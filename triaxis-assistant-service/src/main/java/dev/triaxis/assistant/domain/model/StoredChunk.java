package dev.triaxis.assistant.domain.model;

/** What the store knows about a chunk, enough to decide whether it must be embedded again. */
public record StoredChunk(String id, String contentHash, String embeddingModel) {

	public boolean isCurrent(ProtocolChunk chunk, String model) {
		return contentHash.equals(chunk.contentHash()) && embeddingModel.equals(model);
	}

}
