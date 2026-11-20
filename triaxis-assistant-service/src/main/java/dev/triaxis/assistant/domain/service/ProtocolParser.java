package dev.triaxis.assistant.domain.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.triaxis.assistant.domain.exception.InvalidProtocolException;
import dev.triaxis.assistant.domain.model.ProtocolChunk;

/**
 * Reads a protocol written in Markdown: a title line {@code # PROTO-001: Name} and one {@code ## Section}
 * heading per section. Each section becomes one chunk; the lines of a section are joined with spaces.
 */
public class ProtocolParser {

	private static final Pattern TITLE = Pattern.compile("^#\\s+(PROTO-\\d{3})\\s*:\\s*(.+)$");
	private static final String SECTION_PREFIX = "## ";

	public List<ProtocolChunk> parse(String source, String markdown) {
		List<String> lines = markdown.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
		if (lines.isEmpty()) {
			throw new InvalidProtocolException(source, "the file is empty");
		}
		Matcher title = TITLE.matcher(lines.get(0));
		if (!title.matches()) {
			throw new InvalidProtocolException(source, "the first line must be '# PROTO-NNN: Name'");
		}
		String protocolId = title.group(1);
		String protocolTitle = title.group(2).strip();

		List<ProtocolChunk> chunks = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		String section = null;
		List<String> text = new ArrayList<>();
		for (String line : lines.subList(1, lines.size())) {
			if (line.startsWith(SECTION_PREFIX)) {
				addChunk(source, protocolId, protocolTitle, section, text, chunks, ids);
				section = line.substring(SECTION_PREFIX.length()).strip();
				text = new ArrayList<>();
			}
			else if (section == null) {
				throw new InvalidProtocolException(source, "text before the first '## Section' heading");
			}
			else {
				text.add(line);
			}
		}
		addChunk(source, protocolId, protocolTitle, section, text, chunks, ids);
		if (chunks.isEmpty()) {
			throw new InvalidProtocolException(source, "the protocol has no sections");
		}
		return List.copyOf(chunks);
	}

	private static void addChunk(String source, String protocolId, String protocolTitle, String section,
			List<String> text, List<ProtocolChunk> chunks, Set<String> ids) {
		if (section == null) {
			return;
		}
		if (text.isEmpty()) {
			throw new InvalidProtocolException(source, "section '" + section + "' is empty");
		}
		String id = protocolId + "#" + slug(section);
		if (!ids.add(id)) {
			throw new InvalidProtocolException(source, "two sections have the id " + id);
		}
		chunks.add(ProtocolChunk.of(id, protocolId, protocolTitle, section, String.join(" ", text)));
	}

	/** Lowercase, without accents, words joined by hyphens: {@code Medidas iniciales} gives {@code medidas-iniciales}. */
	static String slug(String text) {
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

}
