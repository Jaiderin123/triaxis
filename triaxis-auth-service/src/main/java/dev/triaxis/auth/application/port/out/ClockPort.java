package dev.triaxis.auth.application.port.out;

import java.time.Instant;

public interface ClockPort {

	Instant now();

}
