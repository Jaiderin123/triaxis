package dev.triaxis.assistant.infrastructure.adapter.out.llm.gemini;

import java.util.List;

/** The JSON the model is asked to return (field names are part of the prompt, not of the external contract). */
record GeminiReply(String answer, List<Integer> excerpts, boolean noProtocol) {
}
