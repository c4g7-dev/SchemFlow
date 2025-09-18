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
    private final String schemPrefix = "SF_";
    private final String defaultGroup;

    public S3Service(String endpoint, String accessKey, String secretKey, String bucket, boolean secure, String extension) {
        this(endpoint, accessKey, secretKey, bucket, secure, extension, "FlowSuite/SchemFlow", "default");
    }

    public S3Service(String endpoint, String accessKey, String secretKey, String bucket, boolean secure, String extension, String rootDir, String defaultGroup) {
        if (endpoint == null || accessKey == null || secretKey == null || bucket == null) {
            throw new IllegalArgumentException("Missing S3 config values");
        }
        this.bucket = bucket;
        this.extension = extension != null && !extension.isBlank() ? (extension.startsWith(".") ? extension : "." + extension) : ".schm";
        this.rootDir = (rootDir == null || rootDir.isBlank()) ? "FlowSuite/SchemFlow" : rootDir.replaceAll("^/+|/+$", "");
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

        String objKey = buildObjectKey(name, group);
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
        if (!objectName.toLowerCase().endsWith(extension)) objectName = objectName + extension;
        String objKey = buildObjectKey(objectName, group);
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
        if (!name.toLowerCase().endsWith(extension)) name = name + extension;
        String objKey = buildObjectKey(name, group);
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objKey).build());
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

    private String buildObjectKey(String fileName, String group) {
        String grp = (group == null || group.isBlank()) ? defaultGroup : group;
        String base = fileName;
        String nameOnly = Path.of(base).getFileName().toString();
        if (!nameOnly.toUpperCase().startsWith(schemPrefix)) {
            String withoutExt = nameOnly.endsWith(extension) ? nameOnly.substring(0, nameOnly.length() - extension.length()) : nameOnly;
            nameOnly = schemPrefix + withoutExt + extension;
        }
        return rootDir + "/" + groupPrefix + grp + "/" + nameOnly;
    }

    @Override
    public void close() { }
}
