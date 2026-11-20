package dev.triaxis.assistant.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorTextTest {

	@Test
	void writesThePgvectorTextForm() {
		assertThat(VectorText.of(new float[] {0.1f, -1f, 2.5f})).isEqualTo("[0.1,-1.0,2.5]");
	}

	@Test
	void rejectsEmptyAndNonFiniteVectors() {
		assertThatThrownBy(() -> VectorText.of(new float[0])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> VectorText.of(new float[] {Float.NaN})).isInstanceOf(IllegalArgumentException.class);
	}

}
