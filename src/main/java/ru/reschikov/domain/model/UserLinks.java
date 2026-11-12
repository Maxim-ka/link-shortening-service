package ru.reschikov.domain.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class UserLinks {


    private final Map<String, Link> userLinks = new HashMap<>();

    public UserLinks(Map.Entry<String, Link> entry) {
        set(entry);
    }

    @JsonCreator
    public UserLinks(@JsonProperty("userLinks") Map<String, Link> map) {
        userLinks.putAll(map) ;
    }

    public void set(Map.Entry<String, Link> entry){
        checkLink(entry.getValue().getUrl());
        userLinks.put(entry.getKey(), entry.getValue());
    }

    public void change(String shortLink, Map.Entry<String, Link> entry){
        userLinks.remove(shortLink);
        userLinks.put(entry.getKey(), entry.getValue());
    }

    public Link getLink(String link){
        return userLinks.get(link);
    }

    public boolean remove(String link) {
        return userLinks.remove(link) != null;
    }

    public void remove(List<String> links) {
        links.forEach(userLinks::remove);
    }

    private void checkLink(String url){
        if (userLinks.isEmpty()) return;
        userLinks.entrySet().stream()
                .filter(entry -> entry.getValue().getUrl().equals(url))
                .map(Map.Entry::getKey)
                .findFirst()
                .ifPresent(userLinks::remove);
    }

    public Map<String, Link> takeAll() {
        return userLinks;
    }
}
