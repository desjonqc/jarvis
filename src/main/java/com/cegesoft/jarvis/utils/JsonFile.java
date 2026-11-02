package com.cegesoft.jarvis.utils;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import static com.cegesoft.jarvis.Jarvis.getLogger;

/**
 * A json file written atomically : the content goes to a temporary file which then replaces the real one,
 * so a power cut (frequent on a Raspberry Pi) can't leave a half-written file.
 */
public class JsonFile {

    private final Path path;

    public JsonFile(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    /**
     * @return the json array stored in the file, or an empty array if the file doesn't exist.
     * An unreadable file is kept aside (.corrupted) instead of being overwritten by the next save.
     */
    public JSONArray readArray() {
        if (!Files.exists(path))
            return new JSONArray();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object json = new JSONParser().parse(reader);
            if (json instanceof JSONArray)
                return (JSONArray) json;
            throw new ParseException(ParseException.ERROR_UNEXPECTED_TOKEN);
        } catch (IOException | ParseException e) {
            Path backup = path.resolveSibling(path.getFileName() + ".corrupted-" + System.currentTimeMillis());
            getLogger().error("Can't read " + path + ", it is moved to " + backup + " : ", e);
            try {
                Files.move(path, backup);
            } catch (IOException moveError) {
                getLogger().error("Can't move " + path + " : ", moveError);
            }
            return new JSONArray();
        }
    }

    public synchronized void write(JSONAware json) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(StandardCharsets.UTF_8.encode(json.toJSONString()));
            channel.force(true);
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
