package ru.reschikov.usecase;

import ru.reschikov.domain.model.Link;


import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Service extends AutoCloseable {

    String create(UUID uuid, String url, int limit);
    Link getLink(UUID uuid, String shortUrl);
    String change(UUID uuid, String shortLink, int limit);
    Map<String, Link> getAllShortLink(UUID uuid);
    boolean remove(UUID uuid, List<String> shortUrls);
    boolean remove(UUID uuid, String shortUrl);
    void remove(UUID uuid);

}
