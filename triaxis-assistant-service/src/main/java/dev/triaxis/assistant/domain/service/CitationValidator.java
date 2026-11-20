package dev.triaxis.assistant.domain.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.model.Source;

/**
 * Checks the model answer against what was retrieved (RN-05, last layer). An answer is accepted only when it has
 * text and cites at least one excerpt, and every cited number is one of the excerpts that were sent. The sources
 * are built from the metadata of those excerpts, in the order they were sent, never from the model text.
 */
public class CitationValidator {

	public ClinicalAnswer validate(ModelAnswer answer, List<NumberedExcerpt> sent) {
		if (answer.noProtocol()) {
			return ClinicalAnswer.noProtocol();
		}
		Map<Integer, NumberedExcerpt> byNumber = sent.stream()
				.collect(Collectors.toMap(NumberedExcerpt::number, Function.identity()));
		Set<Integer> cited = new LinkedHashSet<>(answer.citations());
		if (answer.text().isBlank() || cited.isEmpty() || !byNumber.keySet().containsAll(cited)) {
			return ClinicalAnswer.rejected();
		}
		List<Source> sources = sent.stream()
				.filter(excerpt -> cited.contains(excerpt.number()))
				.map(excerpt -> new Source(excerpt.chunk().protocolId(), excerpt.chunk().section()))
				.distinct()
				.toList();
		return ClinicalAnswer.answered(answer.text(), sources);
	}

}
