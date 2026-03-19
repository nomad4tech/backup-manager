package tech.nomad4.backupmanager.isolate.storage.service;

import lombok.extern.slf4j.Slf4j;
import tech.nomad4.backupmanager.isolate.storage.dto.CleanupPolicy;
import tech.nomad4.backupmanager.isolate.storage.dto.DiskSpaceInfo;
import tech.nomad4.backupmanager.isolate.storage.dto.FileInfo;
import tech.nomad4.backupmanager.isolate.storage.exception.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Universal service for local file-system operations.
 * <p>
 * This service is intentionally domain-agnostic: it knows nothing about backups,
 * databases, or Docker. Any component that needs to create directories, check disk
 * space, list files, or clean up old files can depend on this service.
 * </p>
 * <p>
 * All methods throw {@link StorageException} (unchecked) on failure so callers
 * are not forced to handle checked {@link IOException} directly.
 * </p>
 */
@Slf4j
public class StorageService {

    // -------------------------------------------------------------------------
    // Directory operations
    // -------------------------------------------------------------------------

    /**
     * Ensures the specified directory exists, creating it (and any missing parent
     * directories) if necessary.
     *
     * @param path directory path to create or verify
     * @throws StorageException if the directory cannot be created
     */
    public void ensureDirectory(String path) {
        try {
            Path dir = Paths.get(path);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created directory: {}", path);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to create directory: " + path, e);
        }
    }

    /**
     * Deletes a directory, optionally removing its contents first.
     *
     * @param path      directory path to delete
     * @param recursive if {@code true}, delete all contents recursively;
     *                  if {@code false}, the directory must be empty
     * @throws StorageException if deletion fails
     */
    public void deleteDirectory(String path, boolean recursive) {
        Path dir = Paths.get(path);
        if (!Files.exists(dir)) {
            return;
        }
        try {
            if (recursive) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            } else {
                Files.delete(dir);
            }
            log.info("Deleted directory: {}", path);
        } catch (UncheckedIOException e) {
            throw new StorageException("Failed to delete directory: " + path, e.getCause());
        } catch (IOException e) {
            throw new StorageException("Failed to delete directory: " + path, e);
        }
    }

    // -------------------------------------------------------------------------
    // Disk space
    // -------------------------------------------------------------------------

    /**
     * Returns disk-space statistics for the partition containing {@code path}.
     *
     * @param path any path on the target file system
     * @return disk space information
     * @throws StorageException if the file-store cannot be queried
     */
    public DiskSpaceInfo getAvailableSpace(String path) {
        try {
            Path p = Paths.get(path);
            FileStore store = Files.getFileStore(p);

            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            long used = total - free;
            int usagePercent = (total > 0) ? (int) ((used * 100L) / total) : 0;

            return DiskSpaceInfo.builder()
                    .path(path)
                    .totalBytes(total)
                    .usedBytes(used)
                    .freeBytes(free)
                    .usagePercent(usagePercent)
                    .build();

        } catch (IOException e) {
            throw new StorageException("Failed to get disk space for: " + path, e);
        }
    }

    /**
     * Checks whether the partition containing {@code path} has at least
     * {@code requiredBytes} of usable space.
     *
     * @param path          any path on the target file system
     * @param requiredBytes minimum bytes required
     * @return {@code true} if sufficient space is available
     */
    public boolean hasEnoughSpace(String path, long requiredBytes) {
        DiskSpaceInfo info = getAvailableSpace(path);
        boolean hasSpace = info.getFreeBytes() >= requiredBytes;

        log.debug("Space check for {}: required={}, available={}, sufficient={}",
                path, requiredBytes, info.getFreeBytes(), hasSpace);

        return hasSpace;
    }

    // -------------------------------------------------------------------------
    // Path validation
    // -------------------------------------------------------------------------

    /**
     * Checks whether {@code path} (or its parent directory) is writable.
     *
     * @param path file or directory path to test
     * @return {@code true} if the path is writable
     */
    public boolean validatePath(String path) {
        Path p = Paths.get(path);
        Path target = (p.getParent() != null) ? p.getParent() : p;
        return Files.isWritable(target);
    }

    // -------------------------------------------------------------------------
    // File listing
    // -------------------------------------------------------------------------

    /**
     * Lists regular files in {@code directory} whose names match a glob
     * {@code pattern} (e.g. {@code "*.sql"} or {@code "backup_*"}).
     *
     * @param directory directory to scan
     * @param pattern   glob pattern applied to file names
     * @return list of matching file metadata; empty list if the directory
     *         does not exist
     * @throws StorageException if the directory cannot be read
     */
    public List<FileInfo> listFiles(String directory, String pattern) {
        try {
            Path dir = Paths.get(directory);
            if (!Files.exists(dir)) {
                return List.of();
            }

            PathMatcher matcher = FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern);

            try (var stream = Files.list(dir)) {
                return stream
                        .filter(p -> matcher.matches(p.getFileName()))
                        .filter(Files::isRegularFile)
                        .map(this::pathToFileInfo)
                        .collect(Collectors.toList());
            }

        } catch (IOException e) {
            throw new StorageException("Failed to list files in: " + directory, e);
        }
    }

    // -------------------------------------------------------------------------
    // File operations
    // -------------------------------------------------------------------------

    /**
     * Returns metadata for a specific file.
     *
     * @param filepath absolute or relative file path
     * @return file metadata
     * @throws StorageException if the file does not exist or cannot be read
     */
    public FileInfo getFileInfo(String filepath) {
        Path path = Paths.get(filepath);
        if (!Files.exists(path)) {
            throw new StorageException("File not found: " + filepath);
        }
        return pathToFileInfo(path);
    }

    /**
     * Returns {@code true} if the file at {@code filepath} exists.
     *
     * @param filepath file path to test
     * @return {@code true} if the file exists
     */
    public boolean fileExists(String filepath) {
        return Files.exists(Paths.get(filepath));
    }

    /**
     * Deletes the file at {@code filepath} if it exists.
     *
     * @param filepath file to delete
     * @throws StorageException if deletion fails
     */
    public void deleteFile(String filepath) {
        try {
            Files.deleteIfExists(Paths.get(filepath));
            log.info("Deleted file: {}", filepath);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + filepath, e);
        }
    }

    /**
     * Moves a file, replacing the destination if it already exists.
     *
     * @param from source file path
     * @param to   destination file path
     * @throws StorageException if the move fails
     */
    public void moveFile(String from, String to) {
        try {
            Files.move(Paths.get(from), Paths.get(to),
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved file: {} -> {}", from, to);
        } catch (IOException e) {
            throw new StorageException("Failed to move file: " + from + " -> " + to, e);
        }
    }

    /**
     * Copies a file, replacing the destination if it already exists.
     *
     * @param from source file path
     * @param to   destination file path
     * @throws StorageException if the copy fails
     */
    public void copyFile(String from, String to) {
        try {
            Files.copy(Paths.get(from), Paths.get(to),
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied file: {} -> {}", from, to);
        } catch (IOException e) {
            throw new StorageException("Failed to copy file: " + from + " -> " + to, e);
        }
    }

    /**
     * Heuristically determines whether a file is currently being written by
     * checking whether its last-modified time is within the past 5 seconds.
     *
     * @param filepath file path to check
     * @return {@code true} if the file was modified in the last 5 seconds
     */
    public boolean isFileBeingWritten(String filepath) {
        try {
            Path path = Paths.get(filepath);
            if (!Files.exists(path)) {
                return false;
            }

            FileTime lastModified = Files.getLastModifiedTime(path);
            long secondsAgo = Duration.between(
                    lastModified.toInstant(),
                    Instant.now()
            ).getSeconds();

            return secondsAgo < 5;

        } catch (IOException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Checksum
    // -------------------------------------------------------------------------

    /**
     * Computes a hex-encoded checksum for the given file.
     *
     * @param filepath  file to hash
     * @param algorithm digest algorithm name, e.g. {@code "MD5"}, {@code "SHA-256"}
     * @return lowercase hex string of the computed digest
     * @throws StorageException if the file cannot be read or the algorithm is unknown
     */
    public String calculateChecksum(String filepath, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);

            try (InputStream is = Files.newInputStream(Paths.get(filepath))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }

            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            throw new StorageException("Failed to calculate " + algorithm + " checksum for: " + filepath, e);
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Deletes files in {@code directory} according to the given retention policy.
     * <p>
     * Files are evaluated in ascending modification-time order (oldest first).
     * Deletion errors for individual files are logged but do not abort the cleanup.
     * </p>
     *
     * @param directory directory to clean up
     * @param policy    retention policy controlling which files to delete
     * @return paths of files that were successfully deleted
     */
    public List<String> cleanupFiles(String directory, CleanupPolicy policy) {
        List<FileInfo> files = listFiles(directory, "*");

        // Sort oldest first so index-based operations are predictable
        files.sort(Comparator.comparing(FileInfo::getModified));

        List<String> toDelete = switch (policy.getType()) {
            case COUNT -> selectByCount(files, policy.getKeepLastN());
            case TIME  -> selectByTime(files, policy.getKeepDays());
            case SIZE  -> selectBySize(files, policy.getMaxTotalSizeBytes());
        };

        List<String> deleted = new ArrayList<>();
        for (String filepath : toDelete) {
            try {
                deleteFile(filepath);
                deleted.add(filepath);
            } catch (Exception e) {
                log.error("Failed to delete {} during cleanup: {}", filepath, e.getMessage());
            }
        }

        log.info("Cleanup completed: {} file(s) deleted from {}", deleted.size(), directory);
        return deleted;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> selectByCount(List<FileInfo> files, int keepLastN) {
        if (files.size() <= keepLastN) {
            return List.of();
        }
        // Delete the oldest files at the front of the list
        return files.subList(0, files.size() - keepLastN).stream()
                .map(FileInfo::getPath)
                .collect(Collectors.toList());
    }

    private List<String> selectByTime(List<FileInfo> files, int keepDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
        return files.stream()
                .filter(f -> f.getModified().isBefore(cutoff))
                .map(FileInfo::getPath)
                .collect(Collectors.toList());
    }

    private List<String> selectBySize(List<FileInfo> files, long maxTotalSize) {
        long currentSize = 0;
        List<String> toDelete = new ArrayList<>();

        // Iterate from newest to oldest, keeping files that fit within the budget
        for (int i = files.size() - 1; i >= 0; i--) {
            FileInfo file = files.get(i);
            if (currentSize + file.getSizeBytes() <= maxTotalSize) {
                currentSize += file.getSizeBytes();
            } else {
                toDelete.add(file.getPath());
            }
        }

        return toDelete;
    }

    private FileInfo pathToFileInfo(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

            return FileInfo.builder()
                    .path(path.toString())
                    .name(path.getFileName().toString())
                    .sizeBytes(attrs.size())
                    .created(LocalDateTime.ofInstant(
                            attrs.creationTime().toInstant(),
                            ZoneId.systemDefault()))
                    .modified(LocalDateTime.ofInstant(
                            attrs.lastModifiedTime().toInstant(),
                            ZoneId.systemDefault()))
                    .isDirectory(attrs.isDirectory())
                    .build();

        } catch (IOException e) {
            throw new StorageException("Failed to read file attributes for: " + path, e);
        }
    }
}
