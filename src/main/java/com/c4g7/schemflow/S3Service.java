package com.c4g7.schemflow;

import com.c4g7.schemflow.util.SafeIO;
import com.c4g7.schemflow.util.ZipUtils;
import io.minio.*;
import io.minio.messages.Item;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class S3Service implements AutoCloseable {
    private final MinioClient client;
    private final String bucket;
    private final String extension;
    private final String rootDir;
    private final String groupPrefix = "SF_";
    // Legacy: some older uploads may have had a schematic name prefix. New uploads must NOT use it.
    private final String legacySchemPrefix = "SF_";
    private final String defaultGroup;

    public S3Service(String endpoint, String accessKey, String secretKey, String bucket, boolean secure, String extension) {
    this(endpoint, accessKey, secretKey, bucket, secure, extension, "FlowStack/SchemFlow", "default");
    }

    public S3Service(String endpoint, String accessKey, String secretKey, String bucket, boolean secure, String extension, String rootDir, String defaultGroup) {
        if (endpoint == null || accessKey == null || secretKey == null || bucket == null) {
            throw new IllegalArgumentException("Missing S3 config values");
        }
        this.bucket = bucket;
        this.extension = extension != null && !extension.isBlank() ? (extension.startsWith(".") ? extension : "." + extension) : ".schm";
    this.rootDir = (rootDir == null || rootDir.isBlank()) ? "FlowStack/SchemFlow" : rootDir.replaceAll("^/+|/+$", "");
        this.defaultGroup = (defaultGroup == null || defaultGroup.isBlank()) ? "default" : defaultGroup;
        MinioClient.Builder builder = MinioClient.builder().credentials(accessKey, secretKey);
        String ep = endpoint.trim();
        try {
            if (ep.startsWith("http://") || ep.startsWith("https://")) {
                builder = builder.endpoint(ep);
            } else if (ep.contains(":")) {
                int last = ep.lastIndexOf(':');
                String host = ep.substring(0, last);
                int port = Integer.parseInt(ep.substring(last + 1));
                builder = builder.endpoint(host, port, secure);
            } else {
                int port = secure ? 443 : 9000;
                builder = builder.endpoint(ep, port, secure);
            }
        } catch (Exception e) {
            builder = builder.endpoint(ep);
        }
        this.client = builder.build();
    }

    public List<String> listSchm() throws Exception { return listSchm(defaultGroup); }

    public List<String> listSchm(String group) throws Exception {
        String grp = (group == null || group.isBlank()) ? defaultGroup : group;
        String prefix = rootDir + "/" + groupPrefix + grp + "/";
        Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder().bucket(bucket).recursive(true).prefix(prefix).build()
        );
        List<String> names = new ArrayList<>();
        for (Result<Item> r : results) {
            Item it = r.get();
            String key = it.objectName();
            if (key.toLowerCase().endsWith(extension)) {
                String fileName = Path.of(key).getFileName().toString();
                // Hide legacy schematic prefix from user-facing names
                if (fileName.startsWith(legacySchemPrefix)) fileName = fileName.substring(legacySchemPrefix.length());
                names.add(fileName);
            }
        }
        return names;
    }

    public Path fetchSchm(String name, String destDir) throws Exception { return fetchSchm(name, defaultGroup, destDir); }

    public Path fetchSchm(String name, String group, String destDir) throws Exception {
        if (!name.toLowerCase().endsWith(extension)) name = name + extension;
        Path dir = Path.of(destDir);
        SafeIO.ensureDir(dir);
        Path out = dir.resolve(Path.of(name).getFileName().toString());

        String objKey = resolveObjectKeyForRead(name, group);
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objKey).build())) {
            Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return out;
    }

    public Path fetchAndExtractSchm(String name, String destDir) throws Exception { return fetchAndExtractSchm(name, defaultGroup, destDir); }

    public Path fetchAndExtractSchm(String name, String group, String destDir) throws Exception {
        Path path = fetchSchm(name, group, destDir);
        if (ZipUtils.isZip(path)) {
            Path extractDir = path.getParent().resolve(path.getFileName().toString().replaceFirst("\\.schm$", ""));
            SafeIO.ensureDir(extractDir);
            ZipUtils.unzip(path, extractDir);
            return extractDir;
        }
        return path;
    }

    public void uploadSchm(Path file, String objectName) throws Exception { uploadSchm(file, objectName, defaultGroup); }

    public void uploadSchm(Path file, String objectName, String group) throws Exception {
        ensureValidName(objectName);
        ensureValidGroup(group);
        if (!objectName.toLowerCase().endsWith(extension)) objectName = objectName + extension;
        // Normalize legacy prefix from provided names
        if (objectName.startsWith(legacySchemPrefix)) objectName = objectName.substring(legacySchemPrefix.length());
        String objKey = buildObjectKey(objectName, group);
        // Collision check (also check legacy key for backward-compat)
        if (objectExists(objKey) || objectExists(buildLegacyObjectKey(objectName, group))) {
            throw new IllegalStateException("Name already taken in this group: " + objectName);
        }
        try (InputStream in = Files.newInputStream(file)) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objKey)
                            .contentType("application/octet-stream")
                            .stream(in, Files.size(file), -1)
                            .build()
            );
        }
    }

    public void deleteSchm(String name) throws Exception { deleteSchm(name, defaultGroup); }

    public void deleteSchm(String name, String group) throws Exception {
        ensureValidName(name);
        ensureValidGroup(group);
        if (!name.toLowerCase().endsWith(extension)) name = name + extension;
        String objKey = resolveObjectKeyForRead(name, group);
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objKey).build());
    }

    public void trashSchm(String name) throws Exception { trashSchm(name, defaultGroup); }

    public void trashSchm(String name, String group) throws Exception {
        ensureValidName(name);
        ensureValidGroup(group);
        if (!name.toLowerCase().endsWith(extension)) name = name + extension;
        String src = resolveObjectKeyForRead(name, group);
        String dst = buildTrashKey(name, group);
        client.copyObject(CopyObjectArgs.builder().bucket(bucket).object(dst).source(CopySource.builder().bucket(bucket).object(src).build()).build());
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(src).build());
    }

    public void restoreSchm(String name) throws Exception { restoreSchm(name, defaultGroup); }

    public void restoreSchm(String name, String group) throws Exception {
        ensureValidName(name);
        ensureValidGroup(group);
        if (!name.toLowerCase().endsWith(extension)) name = name + extension;
        String src = buildTrashKey(name, group);
        String dst = buildObjectKey(name, group);
        if (!objectExists(src)) throw new IllegalStateException("No trashed item named " + name + " in group " + group);
        client.copyObject(CopyObjectArgs.builder().bucket(bucket).object(dst).source(CopySource.builder().bucket(bucket).object(src).build()).build());
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(src).build());
    }

    public List<String> listGroups() throws Exception {
        String prefix = rootDir + "/";
        Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder().bucket(bucket).recursive(true).prefix(prefix).build()
        );
        java.util.Set<String> groups = new java.util.HashSet<>();
        for (Result<Item> r : results) {
            Item it = r.get();
            String key = it.objectName();
            if (!key.startsWith(prefix)) continue;
            String rest = key.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                String groupFolder = rest.substring(0, slash);
                if (groupFolder.startsWith(groupPrefix)) {
                    String g = groupFolder.substring(groupPrefix.length());
                    groups.add(g);
                }
            }
        }
        return new java.util.ArrayList<>(groups);
    }

    public void createGroup(String group) throws Exception {
        String grp = (group == null || group.isBlank()) ? defaultGroup : group;
        String key = rootDir + "/" + groupPrefix + grp + "/.keep";
        byte[] empty = new byte[0];
        try (InputStream in = new java.io.ByteArrayInputStream(empty)) {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(in, 0, -1).contentType("application/octet-stream").build());
        }
    }

    public void createRoot() throws Exception {
        String key = rootDir + "/.keep";
        byte[] empty = new byte[0];
        try (InputStream in = new java.io.ByteArrayInputStream(empty)) {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(in, 0, -1).contentType("application/octet-stream").build());
        }
    }

    private String buildObjectKey(String fileName, String group) {
        String grp = (group == null || group.isBlank()) ? defaultGroup : group;
        String nameOnly = Path.of(fileName).getFileName().toString();
        // Ensure extension only; no schematic name prefixing
        if (!nameOnly.toLowerCase().endsWith(extension)) nameOnly = nameOnly + extension;
        return rootDir + "/" + groupPrefix + grp + "/" + nameOnly;
    }

    private String buildLegacyObjectKey(String fileName, String group) {
        String grp = (group == null || group.isBlank()) ? defaultGroup : group;
        String nameOnly = Path.of(fileName).getFileName().toString();
        if (!nameOnly.toLowerCase().endsWith(extension)) nameOnly = nameOnly + extension;
        if (!nameOnly.startsWith(legacySchemPrefix)) nameOnly = legacySchemPrefix + nameOnly;
        return rootDir + "/" + groupPrefix + grp + "/" + nameOnly;
    }

    private String buildTrashKey(String fileName, String group) {
        String grp = (group == null || group.isBlank()) ? defaultGroup : group;
        String nameOnly = Path.of(fileName).getFileName().toString();
        if (!nameOnly.toLowerCase().endsWith(extension)) nameOnly = nameOnly + extension;
        return rootDir + "/.trash/" + groupPrefix + grp + "/" + nameOnly;
    }

    private boolean objectExists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveObjectKeyForRead(String fileName, String group) {
        String primary = buildObjectKey(fileName, group);
        if (objectExists(primary)) return primary;
        String legacy = buildLegacyObjectKey(fileName, group);
        if (objectExists(legacy)) return legacy;
        return primary; // Fall back; will trigger an error when accessed
    }

    private void ensureValidName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
    if (name.contains(":")) throw new IllegalArgumentException("':' is prohibited in names");
        if (name.contains("/")) throw new IllegalArgumentException("'/' is prohibited in names");
    }

    private void ensureValidGroup(String group) {
        if (group == null) return;
    if (group.contains(":")) throw new IllegalArgumentException("':' is prohibited in group names");
        if (group.contains("/")) throw new IllegalArgumentException("'/' is prohibited in group names");
    }

    // --- Path-based listing/fetching under rootDir (not using groups) ---
    public java.util.List<String> listDirectories(String relPath) throws Exception {
        String rp = normalizeRelPath(relPath);
        String prefix = rootDir + "/" + rp;
        if (!prefix.endsWith("/")) prefix += "/";
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).prefix(prefix).build());
        java.util.Set<String> dirs = new java.util.LinkedHashSet<>();
        for (Result<Item> r : results) {
            Item it = r.get();
            String key = it.objectName();
            if (!key.startsWith(prefix)) continue;
            String rest = key.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                String dir = rest.substring(0, slash);
                if (!dir.isBlank() && !dir.startsWith(".")) dirs.add(dir);
            }
        }
        return new java.util.ArrayList<>(dirs);
    }

    public java.util.List<String> listFiles(String relPath) throws Exception {
        String rp = normalizeRelPath(relPath);
        String prefix = rootDir + "/" + rp;
        if (!prefix.endsWith("/")) prefix += "/";
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(false).prefix(prefix).build());
        java.util.List<String> files = new java.util.ArrayList<>();
        for (Result<Item> r : results) {
            Item it = r.get();
            String key = it.objectName();
            if (!key.startsWith(prefix)) continue;
            String rest = key.substring(prefix.length());
            if (!rest.contains("/") && rest.toLowerCase().endsWith(extension)) {
                String base = Path.of(rest).getFileName().toString();
                files.add(base);
            }
        }
        return files;
    }

    public Path fetchByPath(String relFilePath, String destDir) throws Exception {
        String rp = normalizeRelPath(relFilePath);
        if (rp.endsWith("/")) throw new IllegalArgumentException("Path points to a directory");
        if (!rp.toLowerCase().endsWith(extension)) rp = rp + extension;
        String key = rootDir + "/" + rp;
        Path dir = Path.of(destDir);
        SafeIO.ensureDir(dir);
        Path out = dir.resolve(Path.of(rp).getFileName().toString());
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }

    private String normalizeRelPath(String relPath) {
        if (relPath == null) return "";
        String p = relPath.replace("\\", "/");
        if (p.startsWith("/")) p = p.substring(1);
        if (p.contains("..")) throw new IllegalArgumentException("'..' is prohibited in paths");
        if (p.contains(":")) throw new IllegalArgumentException("':' is prohibited in paths");
        return p;
    }

    @Override
    public void close() { }
}
