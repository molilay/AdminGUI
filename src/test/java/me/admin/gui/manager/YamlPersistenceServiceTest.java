package me.admin.gui.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlPersistenceServiceTest {

    @TempDir
    Path temporary;

    @Test
    void atomicallyReplacesContentsWithoutLeavingTemporaryFiles() throws Exception {
        Path target = temporary.resolve("state.yml");
        YamlPersistenceService.atomicWrite(target, "value: one\n".getBytes(StandardCharsets.UTF_8), temporary);
        YamlPersistenceService.atomicWrite(target, "value: two\n".getBytes(StandardCharsets.UTF_8), temporary);
        assertEquals("value: two\n", Files.readString(target, StandardCharsets.UTF_8));
        try (var files = Files.list(temporary)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void refusesWritesOutsideAllowedRoot() {
        Path outside = temporary.resolveSibling("outside.yml");
        assertThrows(IOException.class, () -> YamlPersistenceService.atomicWrite(
                outside, new byte[0], temporary));
    }

    @Test
    void concurrentMultiFileMutationsProduceOneStableSnapshotGeneration() throws Exception {
        Path root = temporary.resolve("live");
        Files.createDirectories(root);
        Path first = root.resolve("first.yml");
        Path second = root.resolve("second.yml");
        writeGeneration(root, first, second, 0);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> {
                for (int generation = 1; generation <= 60; generation++) {
                    writeGeneration(root, first, second, generation);
                    Thread.sleep(10L);
                }
                return null;
            });

            for (int index = 0; index < 20; index++) {
                Path staging = temporary.resolve("snapshot-" + index);
                try (YamlPersistenceService.GenerationSnapshot snapshot = YamlPersistenceService.captureGeneration(
                        root, staging, 2, path -> path.toString().endsWith(".yml"), 20)) {
                    String firstValue = Files.readString(snapshot.directory().resolve("first.yml"), StandardCharsets.UTF_8);
                    String secondValue = Files.readString(snapshot.directory().resolve("second.yml"), StandardCharsets.UTF_8);
                    assertEquals(firstValue, secondValue, "a snapshot must never expose half of a batch generation");
                }
            }
            writer.get(10, TimeUnit.SECONDS);
        }
    }

    private static void writeGeneration(Path root, Path first, Path second, int generation) throws IOException {
        byte[] value = ("generation: " + generation + "\n").getBytes(StandardCharsets.UTF_8);
        LinkedHashMap<Path, byte[]> batch = new LinkedHashMap<>();
        batch.put(first, value);
        batch.put(second, value);
        YamlPersistenceService.atomicBatchWrite(batch, root);
    }
}
