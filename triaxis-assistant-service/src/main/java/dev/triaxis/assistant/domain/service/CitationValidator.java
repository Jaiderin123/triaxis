package dev.triaxis.assistant.domain.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.triaxis.assistant.domain.model.ClinicalAnswer;
import dev.triaxis.assistant.domain.model.ModelAnswer;
import dev.triaxis.assistant.domain.model.NumberedExcerpt;
import dev.triaxis.assistant.domain.model.Source;

/**
 * Checks the model answer against what was retrieved (RN-05, last layer). An answer is accepted only when it has
 * text and cites at least one excerpt, and every cited number is one of the excerpts that were sent. The sources
 * are built from the metadata of those excerpts, in the order they were sent, never from the model text.
 * <p>
 * Every number of the answer (a dose, a percentage, a time) must also be written in a cited excerpt, so a figure
 * the model invented or was led to write by an injected instruction rejects the answer (ADR 0025). A number spelled
 * in words is not caught by this check; the prompt and the citations cover it.
 */
public class CitationValidator {

	/** Integers and decimals with a point or a comma; a run of digits inside a word (PROTO-002) also counts. */
	private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

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
		Set<String> grounded = new LinkedHashSet<>();
		cited.forEach(number -> {
			NumberedExcerpt excerpt = byNumber.get(number);
			grounded.add(String.valueOf(number));
			grounded.addAll(numbers(excerpt.chunk().protocolId() + " " + excerpt.chunk().protocolTitle() + " "
					+ excerpt.chunk().section() + " " + excerpt.chunk().content()));
		});
		if (!grounded.containsAll(numbers(answer.text()))) {
			return ClinicalAnswer.rejected();
		}
		List<Source> sources = sent.stream()
				.filter(excerpt -> cited.contains(excerpt.number()))
				.map(excerpt -> new Source(excerpt.chunk().protocolId(), excerpt.chunk().section()))
				.distinct()
				.toList();
		return ClinicalAnswer.answered(answer.text(), sources);
	}

	/** The numbers of a text, normalized: a comma is a decimal point, and leading zeros are dropped. */
	static Set<String> numbers(String text) {
		Set<String> found = new LinkedHashSet<>();
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			String value = matcher.group().replace(',', '.');
			String integer = value.contains(".") ? value.substring(0, value.indexOf('.')) : value;
			String fraction = value.contains(".") ? value.substring(value.indexOf('.')) : "";
			String trimmed = integer.replaceFirst("^0+(?=\\d)", "");
			found.add(trimmed + fraction);
		}
		return found;
	}

}
