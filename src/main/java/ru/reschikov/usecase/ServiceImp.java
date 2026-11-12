package ru.reschikov.usecase;

import ru.reschikov.data.Storage;
import ru.reschikov.domain.model.Link;
import ru.reschikov.data.StorageProvider;
import ru.reschikov.domain.model.UserLinks;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ServiceImp implements Service {

    private final Map<UUID, UserLinks> usersLinks;
    private final Storage storage;

    public ServiceImp(Storage storage) {
        this.storage = storage;
        usersLinks = storage.upload(StorageProvider.USERS_LINKS_FILE);
    }

    @Override
    public String create(UUID uuid, String url, int limit) {
        Map.Entry<String, Link> entry = Generator.create(uuid, url, limit);
        UserLinks links =  usersLinks.get(uuid);
        if (links != null) {
            links.set(entry);
        } else {
            UserLinks newUser = create(entry);
            usersLinks.put(uuid, newUser);
        }
        return entry.getKey();
    }

    private UserLinks create(Map.Entry<String, Link> entry){
        return new UserLinks(entry);
    }

    @Override
    public Link getLink(UUID uuid, String shortUrl){
        UserLinks links = usersLinks.get(uuid);
        if (links != null) {
            return links.getLink(shortUrl);
        }
        return null;
    }

    @Override
    public String change(UUID uuid, String shortLink, int limit) {
        UserLinks links = usersLinks.get(uuid);
        if (links != null) {
            Link old = links.getLink(shortLink);
            if (old != null) {
                Map.Entry<String, Link> entry = Generator.create(uuid, old.getUrl(), limit);
                links.change(shortLink, entry);
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public Map<String, Link> getAllShortLink(UUID uuid) {
        UserLinks links = usersLinks.get(uuid);
        if (links != null) {
            return links.takeAll();
        }
        return new HashMap<>(0);
    }

    @Override
    public boolean remove(UUID uuid, List<String> shortUrls) {
        UserLinks links = usersLinks.get(uuid);
        if (links != null) {
            links.remove(shortUrls);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(UUID uuid, String shortUrl) {
        UserLinks links = usersLinks.get(uuid);
        if (links != null) {
            return links.remove(shortUrl);
        }
        return false;
    }

    @Override
    public void remove(UUID uuid) {
        usersLinks.remove(uuid);
    }

    @Override
    public void close() throws IOException {
        storage.save(StorageProvider.USERS_LINKS_FILE, usersLinks);
    }

    public static class Generator {

        public static final String PREFIX_LINK = "link/";
        private static final long BIT_MASK = 0x7FFFFFFFFL;

        private static final String BASE62_ALPHABET = Stream.of(
            IntStream.rangeClosed('a', 'z'), IntStream.rangeClosed('A', 'Z'), IntStream.rangeClosed('0', '9')
        ).flatMapToInt(intStream ->  intStream)
            .mapToObj(i -> (char) i)
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();


        public static Map.Entry<String, UUID> create(){
            long hashTime = System.currentTimeMillis() & BIT_MASK;
            UUID uuid = createKey();
            String time = createShortLink(hashTime, "");
            return Map.entry(time, uuid);
        }

        private static UUID createKey() {
            return UUID.randomUUID();
        }

        private static Map.Entry<String, Link> create(UUID uuid, String url, int limit) {
            long time = System.currentTimeMillis();
            long hash = getHash(uuid, time);
            String link = createShortLink(hash, PREFIX_LINK);
            return Map.entry(link, new Link(time, limit,url));
        }

        private static String createShortLink(long hash, String prefix) {
            StringBuilder sb = new StringBuilder(prefix);
            long tmp = hash;
            while (tmp != 0) {
                int remainder = (int) Long.remainderUnsigned(tmp, BASE62_ALPHABET.length());
                sb.append(BASE62_ALPHABET.charAt(remainder));
                tmp = Long.divideUnsigned(tmp, BASE62_ALPHABET.length());
            }
            return sb.toString();
        }

        private static long getHash(UUID uuid, long time) {
            long hashUrl = time & BIT_MASK;
            long hashUUID = uuid.getLeastSignificantBits() & BIT_MASK;
            return hashUrl ^ hashUUID;
        }
    }

}
