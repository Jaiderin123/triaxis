package dev.triaxis.assistant.application.port.out;

import dev.triaxis.assistant.domain.model.AnswerOutcome;

public interface AssistantMetrics {

	void answered(AnswerOutcome outcome);

}
