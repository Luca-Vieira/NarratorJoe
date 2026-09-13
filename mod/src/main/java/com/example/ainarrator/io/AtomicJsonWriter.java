package com.example.ainarrator.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class AtomicJsonWriter {
    private static final Logger LOG = LoggerFactory.getLogger("AI-Narrator-IO");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AtomicJsonWriter() {}

    public static void write(Path target, JsonObject json) throws IOException {
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        String content = GSON.toJson(json);

        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.READ)) {
            ch.force(true);
        } catch (Exception e) {
            LOG.warn("fsync failed on {}: {}", tmp, e.getMessage());
        }

        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LOG.warn("ATOMIC_MOVE not supported, using non-atomic replace: {}", target);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteIfExists(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            LOG.error("Failed to delete {}: {}", target, e.getMessage());
        }
    }

    public static void touch(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(target)) {
            Files.createFile(target);
        }
    }
}
