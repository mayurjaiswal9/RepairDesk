package com.acetelecom.repairdesk.feature.attachment.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acetelecom.repairdesk.config.AppProperties;

/**
 * Local file-system storage (FILE-001).
 * Files are stored under {@code app.storage.location} with non-guessable
 * keys (UUIDs). The interface is intentionally minimal so a future
 * object-storage implementation can be swapped in.
 */
@Service
public class StorageService {

    private final Path baseDir;

    public StorageService(AppProperties appProperties) throws IOException {
        this.baseDir = Paths.get(appProperties.getStorage().getLocation());
        Files.createDirectories(baseDir);
    }

    /**
     * Persists the stream content and returns the non-guessable storage key.
     */
    public String store(InputStream content, String extension) throws IOException {
        String key = UUID.randomUUID() + (extension != null ? "." + extension : "");
        Path target = baseDir.resolve(key);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    /**
     * Opens the file for reading. Caller is responsible for closing the stream.
     */
    public InputStream load(String storageKey) throws IOException {
        Path file = baseDir.resolve(storageKey);
        if (!file.toAbsolutePath().startsWith(baseDir.toAbsolutePath())) {
            throw new SecurityException("Path traversal attempt detected");
        }
        if (!Files.exists(file)) {
            throw new java.io.FileNotFoundException("File not found: " + storageKey);
        }
        return Files.newInputStream(file);
    }

    /**
     * Deletes the stored file (best-effort, no exception if missing).
     */
    public void delete(String storageKey) {
        try {
            Path file = baseDir.resolve(storageKey);
            if (file.toAbsolutePath().startsWith(baseDir.toAbsolutePath())) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
        }
    }
}
