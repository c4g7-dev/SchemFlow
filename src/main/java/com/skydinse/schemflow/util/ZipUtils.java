package com.skydinse.schemflow.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZipUtils — basic ZIP helpers.
 * Author: c4g7
 */
public class ZipUtils {
    public static boolean isZip(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] sig = new byte[4];
            if (is.read(sig) != 4) return false;
            return sig[0] == 'P' && sig[1] == 'K';
        } catch (IOException e) {
            return false;
        }
    }

    public static void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = destDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(destDir)) throw new IOException("Zip traversal attempt: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}