package ru.reschikov.presentation;

import ru.reschikov.usecase.Service;
import ru.reschikov.data.Storage;
import ru.reschikov.domain.model.Link;
import ru.reschikov.data.StorageProvider;
import ru.reschikov.usecase.ServiceImp;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static ru.reschikov.usecase.ServiceImp.Generator.PREFIX_LINK;

public class ControllerImpl implements Controller {

    private static final String PATTERN_NUMBER = "^\\d+$";
    private static final String ADMIN = "admin";
    private static final String DEL = "del";
    private static final String TIME = "time";
    private static final String PREFIX_HTTPS = "https://";
    private static final String PREFIX_HTTP = "http://";
    private static final String LIMIT = "limit";
    private static final String MY = "my";
    private static final String CHANGE = "change";
    private final Service service;
    private final Storage storage;
    private final ExecutorService single;
    private final Map<String, UUID> users;
    private final Settings settings = new Settings();

    public ControllerImpl(Service service, Storage storage, ExecutorService single) {
        this.service = service;
        this.storage = storage;
        this.single = single;
        users = storage.upload(StorageProvider.USERS_FILE);
    }

    @Override
    public String take(String[] strings) {
        switch (strings.length) {
            case 1: {
                return "Ошибка ввода, разделение слов пробелом";
            }
            case 2: {
                if (strings[0].startsWith(PREFIX_HTTP)) {
                    return String.format("Ссылки %s не обрабатываются ", PREFIX_HTTP);
                }
                if (strings[0].startsWith(PREFIX_HTTPS)) {
                    return createLinks(strings[0], strings[1]);
                }
                if (strings[1].equalsIgnoreCase(DEL)) {
                    return removeUser(strings[0]);
                }
                if (strings[1].equalsIgnoreCase(MY)) {
                    return getAll(strings[0]);
                }
                return gotoUrl(strings[0], strings[1]);
            }
            case 3: {
                if (strings[0].equalsIgnoreCase(ADMIN)) {
                    if (strings[1].equalsIgnoreCase(TIME)) {
                        return setTimeActive(strings[2]);
                    }
                    return String.format("команда установки времени активности ссылки - %s. смотри П - помощь", TIME);
                }
                if (strings[1].equalsIgnoreCase(DEL)){
                    return removeLink(strings[0], strings[2]);
                }
                return createLinks(strings[0], strings[1], strings[2]);
            }
            case 4: {
                if (strings[1].equalsIgnoreCase(CHANGE)) {
                    return change(strings[0], strings[2], strings[3]);
                }
                if (strings[2].equalsIgnoreCase(LIMIT)){
                    return setLimit(strings[0], strings[1], strings[3]);
                }
            }
            default:
                return "Непонятная команда, П - помощь";
        }
    }

    private String change(String login, String shortLink, String string){
        try {
            UUID uuid = checkUUID(login);
            String link = checkShortLink(shortLink);
            int limit = checkLimit(string);
            return service.change(uuid, link, limit);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String getAll(String login) {
        try {
            UUID uuid = checkUUID(login);
            Map<String, Link> map = service.getAllShortLink(uuid);
            return getLinks(uuid, map);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String getLinks(UUID uuid, Map<String, Link> map){
        if (map.isEmpty()) {
            return "Пусто";
        }
        StringBuffer sb = new StringBuffer();
        Map<Boolean, List<Map.Entry<String, Link>>> partitionMap = map.entrySet().parallelStream().collect(
                Collectors.partitioningBy(e -> e.getValue().isActive(settings.timeActive) && e.getValue().hasLimitFollow()));
        processLinks(uuid, sb, partitionMap);
        return sb.toString();
    }

    private void processLinks(UUID uuid, StringBuffer sb, Map<Boolean, List<Map.Entry<String, Link>>> partitionMap){
        CompletableFuture<Void> partTrue = CompletableFuture.runAsync(() -> {
            List<Map.Entry<String, Link>> listTrue = partitionMap.get(true);
            if (!listTrue.isEmpty()) {
                listTrue.forEach(e -> sb.append(String.format("%s лимит %s\n", e.getKey(), e.getValue())));
            }
        });
        CompletableFuture<Void> partFalse = CompletableFuture.runAsync(() -> {
            List<String> listFalse = partitionMap.get(false).stream().map(Map.Entry::getKey).toList();
            if (!listFalse.isEmpty()) {
                if (service.remove(uuid, listFalse)){
                    listFalse.forEach(s -> sb.append(String.format("%s истек срок действия, ссылка удалена\n", s)));
                } else {
                    listFalse.forEach(s -> sb.append(String.format("%s истек срок действия, ссылка будет удалена\n", s)));
                }
            }
        });
        CompletableFuture.allOf(partTrue, partFalse).join();
    }

    private String gotoUrl(String login, String shortLink){
        try {
            UUID uuid = checkUUID(login);
            String checked = checkShortLink(shortLink);
            return followLinks(uuid, checked);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String removeUser(String login){
        try {
            UUID uuid = checkUUID(login);
            service.remove(uuid);
            users.remove(login);
            return "Пользователь удален";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String setLimit(String login, String shortLink, String string){
        try {
            UUID uuid = checkUUID(login);
            String checked = checkShortLink(shortLink);
            int limit = checkLimit(string);
            Link link = service.getLink(uuid, checked);
            link.setLimitFollow(limit);
            return String.format("Для ссылки %s установлен новый лимит %d", checked, limit);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String removeLink(String name, String string){
        try {
            UUID uuid = checkUUID(name);
            String link = checkShortLink(string);
            if (service.remove(uuid, link)){
                return String.format("ссылка %s удалена", link);
            }
            return "Ошибка в ссылке " + link;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String checkShortLink(String string) throws Exception{
        if (!string.startsWith(PREFIX_LINK)) {
            throw new Exception(String.format("Короткая ссылка начинается с %s", PREFIX_LINK));
        }
        if (string.length() == PREFIX_LINK.length()) {
            throw new Exception(String.format("Пустая ссылка %s", string));
        }
        return string;
    }

    private String setTimeActive(String string) {
        if (!string.matches(PATTERN_NUMBER)) {
            return "Время задания активности ссылки - целое число, сек " + string;
        }
        long time = Long.parseLong(string);
        if (time < 300) {
            return "Время задания активности ссылки не может быть меньше 5 мин(300 сек) " + time;
        }
        settings.timeActive = time * 1000;
        saveSetting();
        return null;
    }

    private String createLinks(String login, String url, String string){
        try {
            UUID uuid = checkUUID(login);
            int limit = checkLimitUrl(url, string);
            return service.create(uuid, url, limit);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private UUID checkUUID(String string) throws Exception{
        UUID uuid = users.get(string);
        if (uuid == null) {
            throw new Exception("Ошибка логина " + string);
        }
        return uuid;
    }

    private String createLinks(String url, String string){
        try {
            int limit = checkLimitUrl(url, string);
            return create(url, limit);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private int checkLimitUrl(String url, String string) throws Exception{
        checkUrl(url);
        return checkLimit(string);
    }

    private void checkUrl(String url) throws Exception {
        if (!url.startsWith(PREFIX_HTTPS)){
            throw new Exception(String.format("Ссылка для преобразования начинается с %s", PREFIX_HTTPS));
        }
        if (url.length() == PREFIX_HTTPS.length()){
            throw new Exception(String.format("Пустая ссылка с %s", url));
        }
        try {
            URI uri = new URI(url);
            int port = uri.getPort();
            if (port != -1 && (port < 1 || port > 65535)) throw new IllegalArgumentException();
        } catch (URISyntaxException | IllegalArgumentException e ) {
            throw new Exception("Синтаксические ошибки в ссылке " + url);
        }
    }

    private int checkLimit(String string) throws Exception{
        if (!string.matches(PATTERN_NUMBER)){
            throw new Exception("Лимит переходов по ссылки целое число, " + string);
        }
        int limit = Integer.parseInt(string);
        if (limit == 0) {
            throw new Exception("Лимит переходов по ссылки должен быть > 0");
        }
        return limit;
    }

    private String followLinks(UUID uuid, String shortLink){
        Link link = service.getLink(uuid, shortLink);
        if (link == null) {
            return "Такая ссылка не существует";
        }
        if (!link.isActive(settings.timeActive)){
            service.remove(uuid, shortLink);
            return "Время действия ссылки закончилось, создайте новую";
        }
        if (!link.hasLimitFollow()) {
            int limit = link.getLimitFollow();
            service.remove(uuid, shortLink);
            return String.format("Остаток переходов по ссылке %s, создайте новую", limit);
        }
        try {
            int count = link.count();
            follow(link.getUrl());
            if (count > 0) {
                return String.format("осталось %s переходов", count);
            }
            service.remove(uuid, shortLink);
            return String.format("Остаток переходов %d, ссылка удалена", link.getLimitFollow());
        } catch (URISyntaxException e) {
            return "Ошибка ссылки " + e.getMessage();
        } catch (IOException e) {
            return "Ошибка запуска браузера " + e.getMessage();
        }
    }

    private void follow(String url) throws URISyntaxException, IOException {
        Desktop.getDesktop().browse(new URI(url));
    }

    private String create(String url, int limit){
        Map.Entry<String, UUID> user = ServiceImp.Generator.create();
        String link = service.create(user.getValue(), url, limit);
        users.put(user.getKey(), user.getValue());
        return String.format("Ваш логин в системе: %s\nВаша ссылка %s", user.getKey(), link);
    }

    @Override
    public void close() throws Exception {
        storage.save(StorageProvider.USERS_FILE, users);
        service.close();
    }

    private void saveSetting(){
        CompletableFuture.runAsync(
            () -> {
                try {
                    settings.write();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }, single
        );
    }

    private static class Settings {

        private static final long TIME_ACTIVE_HOUR = 3_600_000L;
        private static final Path PATH_SETTING = Path.of("setting.txt");
        private static final String KEY_TIME_ACTIVE = "timeActive";
        private static final String COMMENT_SETTING = "Setting";
        private long timeActive;

        {
            if (!upload()) {
                timeActive = TIME_ACTIVE_HOUR;
            }
        }

        private void write() throws IOException {
            Properties properties = new Properties();
            properties.put(KEY_TIME_ACTIVE, String.valueOf(timeActive));
            try(
                OutputStream os = Files.newOutputStream(PATH_SETTING)
            ) {
                properties.store(os, COMMENT_SETTING);
            }
        }

        private boolean upload() {
            try {
                if (Files.notExists(PATH_SETTING) || Files.size(PATH_SETTING) == 0) {
                    return false;
                }
                try(
                    InputStream is = Files.newInputStream(PATH_SETTING)
                ) {

                    Properties properties = new Properties();
                    properties.load(is);
                    for (String key : properties.stringPropertyNames()) {
                        if (key.equals(KEY_TIME_ACTIVE)) {
                            timeActive = Long.parseLong(properties.getProperty(key));
                        }
                    }
                    return true;
                }
            } catch (IOException e) {
                return false;
            }
        }
    }
}
