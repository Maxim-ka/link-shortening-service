package ru.reschikov.data;

import java.io.IOException;
import java.util.Map;

public interface Storage {

    String USERS_FILE = "Users.json";
    String USERS_LINKS_FILE = "UsersLinks.json";


    void save (String path, Map<?,?> map) throws IOException;
    <K, V> Map<K, V> upload (String path);
}
