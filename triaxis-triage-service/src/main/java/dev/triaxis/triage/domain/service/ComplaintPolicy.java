package dev.triaxis.triage.domain.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a chief complaint is administrative. Matching is an exact comparison after
 * normalization (lowercase, accents removed, whitespace collapsed), not a "contains" check.
 */
public class ComplaintPolicy {

	private final Set<String> administrative;

	public ComplaintPolicy(Set<String> administrativeComplaints) {
		this.administrative = administrativeComplaints.stream()
				.map(ComplaintPolicy::normalize)
				.collect(Collectors.toUnmodifiableSet());
	}

	public boolean isAdministrative(String complaint) {
		return complaint != null && administrative.contains(normalize(complaint));
	}

	static String normalize(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.strip()
				.replaceAll("\\s+", " ");
	}

}
