package dev.triaxis.assistant.infrastructure.adapter.out.llm.mock;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic embedding for the {@code mock} provider: Spanish words are normalized (lowercase, no accents),
 * common words are dropped, the rest are cut to a short stem and hashed into a fixed number of dimensions. Two
 * texts are close when they share stems. It needs no network and gives the same vector on every machine.
 */
final class BagOfWordsEmbedding {

	static final int DIMENSIONS = 512;
	private static final int STEM_LENGTH = 5;

	private static final Set<String> STOPWORDS = Set.of("a", "al", "ante", "antes", "como", "con", "cual", "cuales",
			"cuando", "de", "del", "el", "en", "es", "esta", "este", "hay", "la", "las", "lo", "los", "mas", "no",
			"o", "para", "por", "que", "se", "si", "sin", "son", "su", "sus", "tras", "un", "una", "unas", "unos",
			"y");

	private BagOfWordsEmbedding() {
	}

	static float[] embed(String text) {
		float[] vector = new float[DIMENSIONS];
		for (String stem : stems(text)) {
			vector[Math.floorMod(stem.hashCode(), DIMENSIONS)] += 1f;
		}
		double norm = 0;
		for (float value : vector) {
			norm += value * value;
		}
		if (norm == 0) {
			// A text without content words still needs a valid vector; it is far from every protocol.
			vector[0] = 1f;
			return vector;
		}
		float length = (float) Math.sqrt(norm);
		for (int i = 0; i < vector.length; i++) {
			vector[i] /= length;
		}
		return vector;
	}

	static List<String> stems(String text) {
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
		return Arrays.stream(plain.split("[^a-z0-9]+"))
				.filter(word -> word.length() > 1 && !STOPWORDS.contains(word))
				.map(word -> word.length() > STEM_LENGTH ? word.substring(0, STEM_LENGTH) : word)
				.toList();
	}

}
