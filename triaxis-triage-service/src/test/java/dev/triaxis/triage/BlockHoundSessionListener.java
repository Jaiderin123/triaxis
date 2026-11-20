package dev.triaxis.triage;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import reactor.blockhound.BlockHound;

/**
 * Installs BlockHound when the test JVM starts, before any Reactor scheduler thread exists. Installing it later
 * (inside a test) instruments threads that are already parked, which breaks the {@code parallel} scheduler and
 * makes every later timeout hang. As a result every test of the service also runs under BlockHound.
 */
public class BlockHoundSessionListener implements LauncherSessionListener {

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		BlockHound.install();
	}

}
