package com.skydinse.schemflow.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SafeIO — file utilities.
 * Author: c4g7
 */
public class SafeIO {
    public static void ensureDir(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
}