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

    public S3Service(String endpoint, String accessKey, String secretKey, String bucket, boolean secure, String extension) {
        if (endpoint == null || accessKey == null || secretKey == null || bucket == null) {
            throw new IllegalArgumentException("Missing S3 config values");
        }
        this.bucket = bucket;
        this.extension = extension != null && !extension.isBlank() ? (extension.startsWith(".") ? extension : "." + extension) : ".schm";
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

    public List<String> listSchm() throws Exception {
        Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder().bucket(bucket).recursive(true).prefix("").build()
        );
        List<String> names = new ArrayList<>();
        for (Result<Item> r : results) {
            Item it = r.get();
            String key = it.objectName();
            if (key.toLowerCase().endsWith(extension)) {
                names.add(key);
            }
        }
        return names;
    }

    public Path fetchSchm(String name, String destDir) throws Exception {
        if (!name.toLowerCase().endsWith(extension)) name = name + extension;
        Path dir = Path.of(destDir);
        SafeIO.ensureDir(dir);
        Path out = dir.resolve(Path.of(name).getFileName().toString());

        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(name).build())) {
            Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return out;
    }

    public Path fetchAndExtractSchm(String name, String destDir) throws Exception {
        Path path = fetchSchm(name, destDir);
        if (ZipUtils.isZip(path)) {
            Path extractDir = path.getParent().resolve(path.getFileName().toString().replaceFirst("\\.schm$", ""));
            SafeIO.ensureDir(extractDir);
            ZipUtils.unzip(path, extractDir);
            return extractDir;
        }
        return path;
    }

    public void uploadSchm(Path file, String objectName) throws Exception {
        if (!objectName.toLowerCase().endsWith(extension)) objectName = objectName + extension;
        try (InputStream in = Files.newInputStream(file)) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .contentType("application/octet-stream")
                            .stream(in, Files.size(file), -1)
                            .build()
            );
        }
    }

    public void deleteSchm(String name) throws Exception {
        if (!name.toLowerCase().endsWith(extension)) name = name + extension;
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(name).build());
    }

    @Override
    public void close() { }
}
