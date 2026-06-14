package com.c4g7.schemflow.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SafeIO {
    public static void ensureDir(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    /** Recursively delete a file or directory tree. No-op if it does not exist. */
    public static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
