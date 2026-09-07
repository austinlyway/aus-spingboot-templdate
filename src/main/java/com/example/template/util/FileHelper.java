package com.example.template.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Project-wide file utility.
 *
 * <p>All file read / write / create operations should go through this class so we have
 * a single place to control encoding, error handling, and (future) auditing / metrics.
 *
 * <p>The class deliberately wraps {@link IOException} in an unchecked {@link FileHelperException}
 * so callers do not need a try/catch on every read.
 */
@Slf4j
public final class FileHelper {

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private FileHelper() {
    }

    // ------------------------------------------------------------
    // Existence / type checks
    // ------------------------------------------------------------

    public static boolean exists(String path) {
        return path != null && Files.exists(Paths.get(path));
    }

    public static boolean isFile(String path) {
        return path != null && Files.isRegularFile(Paths.get(path));
    }

    public static boolean isDirectory(String path) {
        return path != null && Files.isDirectory(Paths.get(path));
    }

    // ------------------------------------------------------------
    // Read
    // ------------------------------------------------------------

    /**
     * Read the entire file as UTF-8 text.
     */
    public static String readText(String path) {
        return readText(path, DEFAULT_CHARSET);
    }

    public static String readText(String path, Charset charset) {
        try {
            return Files.readString(Paths.get(path), charset);
        } catch (IOException e) {
            throw new FileHelperException("Failed to read text file: " + path, e);
        }
    }

    /**
     * Read all lines of a UTF-8 file.
     */
    public static List<String> readLines(String path) {
        return readLines(path, DEFAULT_CHARSET);
    }

    public static List<String> readLines(String path, Charset charset) {
        try {
            return Files.readAllLines(Paths.get(path), charset);
        } catch (IOException e) {
            throw new FileHelperException("Failed to read lines from: " + path, e);
        }
    }

    /**
     * Read the entire file as raw bytes.
     */
    public static byte[] readBytes(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new FileHelperException("Failed to read bytes from: " + path, e);
        }
    }

    /**
     * Open an {@link InputStream} over a file. The caller is responsible for closing it.
     * Provided primarily for callers that need streaming access (e.g. multipart uploads).
     */
    public static InputStream openInputStream(String path) {
        try {
            return Files.newInputStream(Paths.get(path));
        } catch (IOException e) {
            throw new FileHelperException("Failed to open input stream: " + path, e);
        }
    }

    // ------------------------------------------------------------
    // Write
    // ------------------------------------------------------------

    /**
     * Write text to a file in UTF-8, creating it (and any missing parent directories)
     * if it does not exist and overwriting any existing content.
     */
    public static void writeText(String path, String content) {
        writeText(path, content, DEFAULT_CHARSET, true);
    }

    public static void writeText(String path, String content, boolean overwrite) {
        writeText(path, content, DEFAULT_CHARSET, overwrite);
    }

    public static void writeText(String path, String content, Charset charset, boolean overwrite) {
        Path target = Paths.get(path);
        ensureParentDirectory(target);
        try {
            StandardOpenOption[] options = overwrite
                    ? new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                    : new StandardOpenOption[] {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
            Files.writeString(target, content, charset, options);
        } catch (IOException e) {
            throw new FileHelperException("Failed to write text file: " + path, e);
        }
    }

    /**
     * Append text in UTF-8 to a file, creating it if it does not exist.
     */
    public static void appendText(String path, String content) {
        Path target = Paths.get(path);
        ensureParentDirectory(target);
        try (BufferedWriter writer = Files.newBufferedWriter(target, DEFAULT_CHARSET,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(content);
        } catch (IOException e) {
            throw new FileHelperException("Failed to append to file: " + path, e);
        }
    }

    public static void writeBytes(String path, byte[] bytes) {
        writeBytes(path, bytes, true);
    }

    public static void writeBytes(String path, byte[] bytes, boolean overwrite) {
        Path target = Paths.get(path);
        ensureParentDirectory(target);
        try {
            StandardOpenOption[] options = overwrite
                    ? new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                    : new StandardOpenOption[] {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
            Files.write(target, bytes, options);
        } catch (IOException e) {
            throw new FileHelperException("Failed to write bytes to: " + path, e);
        }
    }

    // ------------------------------------------------------------
    // Create / delete
    // ------------------------------------------------------------

    /**
     * Create the file (as an empty file) if it does not already exist.
     */
    public static void createIfAbsent(String path) {
        Path target = Paths.get(path);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            if (!Files.exists(target)) {
                Files.createFile(target);
            }
        } catch (IOException e) {
            throw new FileHelperException("Failed to create file: " + path, e);
        }
    }

    /**
     * Create a directory (and any missing parents). No-op if it already exists.
     */
    public static void mkdirs(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            throw new FileHelperException("Failed to create directory: " + path, e);
        }
    }

    /**
     * Delete a file. Returns {@code true} if it existed and was deleted, {@code false}
     * if it was already absent. Directories must be empty (use {@link #deleteRecursively}
     * otherwise).
     */
    public static boolean delete(String path) {
        try {
            return Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            throw new FileHelperException("Failed to delete: " + path, e);
        }
    }

    /**
     * Recursively delete a file or directory. Returns {@code true} if anything was deleted.
     */
    public static boolean deleteRecursively(String path) {
        Path target = Paths.get(path);
        if (!Files.exists(target)) {
            return false;
        }
        try (var stream = Files.walk(target)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new FileHelperException("Failed to delete: " + p, e);
                        }
                    });
            return true;
        } catch (IOException e) {
            throw new FileHelperException("Failed to walk for deletion: " + path, e);
        }
    }

    // ------------------------------------------------------------
    // Copy / move
    // ------------------------------------------------------------

    public static void copy(String source, String target) {
        copy(source, target, true);
    }

    /**
     * Copy a file or directory. When {@code overwrite} is {@code false} and the target
     * already exists, an {@link FileHelperException} is thrown.
     */
    public static void copy(String source, String target, boolean overwrite) {
        Path src = Paths.get(source);
        Path dst = Paths.get(target);
        ensureParentDirectory(dst);
        try {
            StandardCopyOption[] options = overwrite
                    ? new StandardCopyOption[] {StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[0];
            if (Files.isDirectory(src)) {
                // Manual recursive copy — Files.copy does not handle directories.
                copyDirectoryRecursively(src, dst);
            } else {
                Files.copy(src, dst, options);
            }
        } catch (IOException e) {
            throw new FileHelperException("Failed to copy " + source + " -> " + target, e);
        }
    }

    public static void move(String source, String target) {
        move(source, target, true);
    }

    public static void move(String source, String target, boolean overwrite) {
        Path src = Paths.get(source);
        Path dst = Paths.get(target);
        ensureParentDirectory(dst);
        try {
            StandardCopyOption[] options;
            if (overwrite) {
                options = new StandardCopyOption[] {StandardCopyOption.REPLACE_EXISTING};
                try {
                    Files.move(src, dst, options);
                    return;
                } catch (AtomicMoveNotSupportedException ignored) {
                    // Fall back to non-atomic copy + delete below.
                }
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(src);
            } else {
                Files.move(src, dst);
            }
        } catch (IOException e) {
            throw new FileHelperException("Failed to move " + source + " -> " + target, e);
        }
    }

    // ------------------------------------------------------------
    // Resource loading (classpath)
    // ------------------------------------------------------------

    /**
     * Read a classpath resource as UTF-8 text. Returns {@code null} if not found.
     */
    public static String readClasspathText(String resourcePath) {
        try (InputStream in = FileHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), DEFAULT_CHARSET);
        } catch (IOException e) {
            throw new FileHelperException("Failed to read classpath resource: " + resourcePath, e);
        }
    }

    public static List<String> readClasspathLines(String resourcePath) {
        String text = readClasspathText(resourcePath);
        if (text == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            out.add(line);
        }
        return out;
    }

    // ------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------

    private static void ensureParentDirectory(Path target) {
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new FileHelperException("Failed to create parent directory: " + parent, e);
            }
        }
    }

    private static void copyDirectoryRecursively(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                Path dst = target.resolve(source.relativize(src).toString());
                try {
                    if (Files.isDirectory(src)) {
                        if (!Files.exists(dst)) {
                            Files.createDirectories(dst);
                        }
                    } else {
                        ensureParentDirectory(dst);
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new FileHelperException("Failed to copy " + src + " -> " + dst, e);
                }
            });
        }
    }
}