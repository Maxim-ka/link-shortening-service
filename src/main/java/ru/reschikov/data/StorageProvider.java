package ru.reschikov.data;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import ru.reschikov.domain.model.UserLinks;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StorageProvider implements Storage {
    private static final Path PATH_DATABASE = Path.of("database");
    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        SimpleModule module = new SimpleModule();
        module.addKeyDeserializer(UUID.class, new UuidKeyDeserializer());
        mapper.registerModule(module);
    }

    @Override
    public void save(String path, Map<?, ?> map) throws IOException {
        synchronized (this) {
            if (Files.notExists(PATH_DATABASE)) {
                Files.createDirectories(PATH_DATABASE);
            }
            Path file = getPathFile(path);
            mapper.writeValue(file.toFile(), map);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Map<K, V> upload (String path) {
        synchronized (this) {
            Path file = getPathFile(path);
            if (Files.notExists(file)) {
                return new HashMap<>();
            }
            try {
                if (path.equalsIgnoreCase(USERS_FILE)) {
                    return (Map<K, V>) mapper.readValue(file.toFile(), new TypeReference<Map<String, UUID>>() {});
                }
                if (path.equalsIgnoreCase(USERS_LINKS_FILE)) {
                    return (Map<K, V>) mapper.readValue(file.toFile(), new TypeReference<Map<UUID, UserLinks>>() {});
                }
                return new HashMap<>();
            } catch (IOException e) {
                e.printStackTrace();
                return new HashMap<>();
            }
        }
    }

    private static Path getPathFile(String path){
        return PATH_DATABASE.resolve(path);
    }

    private static class UuidKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String s, DeserializationContext deserializationContext) throws IOException {
            return UUID.fromString(s);
        }
    }
}



