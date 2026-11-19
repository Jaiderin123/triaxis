package dev.triaxis.triage.application.port.out;

import java.util.UUID;

public interface IdGenerator {

	UUID next();

}
