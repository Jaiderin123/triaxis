package dev.triaxis.assistant.infrastructure.adapter.out.persistence;

/** The text form of a pgvector value, for example {@code [0.1,0.25,-1.0]}. */
final class VectorText {

	private VectorText() {
	}

	static String of(float[] values) {
		if (values.length == 0) {
			throw new IllegalArgumentException("An embedding needs at least one dimension");
		}
		StringBuilder text = new StringBuilder(values.length * 10).append('[');
		for (int i = 0; i < values.length; i++) {
			if (!Float.isFinite(values[i])) {
				throw new IllegalArgumentException("An embedding value must be finite");
			}
			if (i > 0) {
				text.append(',');
			}
			text.append(values[i]);
		}
		return text.append(']').toString();
	}

}
