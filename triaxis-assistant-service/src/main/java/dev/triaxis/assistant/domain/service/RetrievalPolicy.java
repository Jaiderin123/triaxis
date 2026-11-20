package dev.triaxis.assistant.domain.service;

import java.util.ArrayList;
import java.util.List;

import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.model.RetrievedExcerpt;

/**
 * The retrieval gate (RN-05, first layer): only excerpts within {@code maxDistance} reach the model. When none
 * does, the question has no protocol and the model is not called.
 *
 * See ADR 0007.
 */
public class RetrievalPolicy {

	private final int topK;
	private final double maxDistance;

	public RetrievalPolicy(int topK, double maxDistance) {
		if (topK < 1) {
			throw new IllegalArgumentException("top-k must be at least 1");
		}
		if (maxDistance <= 0 || maxDistance >= 2) {
			throw new IllegalArgumentException("The maximum cosine distance must be between 0 and 2");
		}
		this.topK = topK;
		this.maxDistance = maxDistance;
	}

	public int topK() {
		return topK;
	}

	/** Keeps the excerpts within the threshold, closest first, and numbers them from 1. */
	public List<NumberedExcerpt> relevant(List<RetrievedExcerpt> retrieved) {
		List<NumberedExcerpt> relevant = new ArrayList<>();
		retrieved.stream()
				.filter(excerpt -> excerpt.distance() <= maxDistance)
				.sorted((a, b) -> Double.compare(a.distance(), b.distance()))
				.limit(topK)
				.forEach(excerpt -> relevant.add(new NumberedExcerpt(relevant.size() + 1, excerpt.chunk())));
		return List.copyOf(relevant);
	}

}
