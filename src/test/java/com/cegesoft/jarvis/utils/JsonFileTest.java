package com.cegesoft.jarvis.utils;

import org.json.simple.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JsonFileTest {

    @TempDir
    Path dir;

    @Test
    @SuppressWarnings("unchecked")
    void writesThenReadsInMissingFolder() throws Exception {
        JsonFile file = new JsonFile(dir.resolve("Module/modules.json"));
        assertTrue(file.readArray().isEmpty());

        JSONArray array = new JSONArray();
        array.add("salon");
        file.write(array);

        assertEquals(array, file.readArray());
        assertFalse(Files.exists(dir.resolve("Module/modules.json.tmp")));
    }

    @Test
    void corruptedFileIsKeptAside() throws Exception {
        Path path = dir.resolve("schedules.json");
        Files.writeString(path, "[{\"half\":");
        JsonFile file = new JsonFile(path);

        assertTrue(file.readArray().isEmpty());
        assertFalse(Files.exists(path));
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("schedules.json.corrupted-")));
        }
    }
}
