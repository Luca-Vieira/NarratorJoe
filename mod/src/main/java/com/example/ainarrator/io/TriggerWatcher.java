package com.example.ainarrator.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class TriggerWatcher implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger("AI-Narrator-Trigger");

    private final Path bridgePath;
    private final Runnable onTrigger;
    private final Path triggerFlag;
    private final long pollIntervalMs;
    private volatile boolean running = true;

    public TriggerWatcher(Path bridgePath, long pollIntervalMs, Runnable onTrigger) {
        this.bridgePath = bridgePath;
        this.pollIntervalMs = pollIntervalMs;
        this.onTrigger = onTrigger;
        this.triggerFlag = bridgePath.resolve("trigger.flag");
    }

    @Override
    public void run() {
        LOG.info("TriggerWatcher started. Monitoring: {}", triggerFlag);
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (Files.exists(triggerFlag)) {
                    LOG.debug("trigger.flag detected!");
                    AtomicJsonWriter.deleteIfExists(triggerFlag);
                    onTrigger.run();
                }
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Error in TriggerWatcher: {}", e.getMessage(), e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        LOG.info("TriggerWatcher stopped.");
    }

    public void stop() {
        this.running = false;
    }
}
