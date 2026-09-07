package com.example.template.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FileHelper}.
 */
class FileHelperTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("file-helper-test-");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            FileHelper.deleteRecursively(tempDir.toString());
        }
    }

    private String p(String name) {
        return tempDir.resolve(name).toString();
    }

    @Test
    @DisplayName("writeText / readText round-trip")
    void writeReadText() {
        String path = p("hello.txt");
        FileHelper.writeText(path, "hello world");
        assertTrue(FileHelper.exists(path));
        assertEquals("hello world", FileHelper.readText(path));
    }

    @Test
    @DisplayName("appendText appends without truncating")
    void appendText() {
        String path = p("log.txt");
        FileHelper.writeText(path, "line1\n");
        FileHelper.appendText(path, "line2\n");
        List<String> lines = FileHelper.readLines(path);
        assertEquals(2, lines.size());
        assertEquals("line1", lines.get(0));
        assertEquals("line2", lines.get(1));
    }

    @Test
    @DisplayName("writeBytes / readBytes round-trip")
    void writeReadBytes() {
        String path = p("blob.bin");
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        FileHelper.writeBytes(path, data);
        assertArrayEquals(data, FileHelper.readBytes(path));
    }

    @Test
    @DisplayName("createIfAbsent is idempotent")
    void createIfAbsentIdempotent() {
        String path = p("a/b/c/marker.txt");
        FileHelper.createIfAbsent(path);
        assertTrue(FileHelper.exists(path));
        FileHelper.createIfAbsent(path); // no exception
        assertTrue(FileHelper.exists(path));
    }

    @Test
    @DisplayName("delete removes files and returns true only when something was removed")
    void deleteBehavior() {
        String path = p("remove.txt");
        assertFalse(FileHelper.delete(path));
        FileHelper.writeText(path, "x");
        assertTrue(FileHelper.delete(path));
        assertFalse(FileHelper.exists(path));
    }

    @Test
    @DisplayName("deleteRecursively removes directories with contents")
    void deleteRecursively() {
        String dir = p("nested");
        FileHelper.mkdirs(dir + "/a/b");
        FileHelper.writeText(dir + "/a/b/c.txt", "x");
        assertTrue(FileHelper.deleteRecursively(dir));
        assertFalse(FileHelper.exists(dir));
    }

    @Test
    @DisplayName("copy duplicates content; overwrite=true replaces")
    void copy() {
        String src = p("src.txt");
        String dst = p("dst.txt");
        FileHelper.writeText(src, "abc");
        FileHelper.copy(src, dst);
        assertEquals("abc", FileHelper.readText(dst));

        FileHelper.writeText(src, "xyz");
        FileHelper.copy(src, dst, true);
        assertEquals("xyz", FileHelper.readText(dst));
    }

    @Test
    @DisplayName("readClasspathText returns null for missing resources")
    void readClasspathMissing() {
        assertNotNull(FileHelper.readClasspathText("application.yml")); // from test resources
        assertEquals(null, FileHelper.readClasspathText("definitely-not-here.txt"));
    }

    @Test
    @DisplayName("exists distinguishes file vs directory")
    void existsFlags() {
        String path = p("some.txt");
        assertFalse(FileHelper.exists(path));
        FileHelper.writeText(path, "x");
        assertTrue(FileHelper.exists(path));
        assertTrue(FileHelper.isFile(path));
        assertFalse(FileHelper.isDirectory(path));
    }
}