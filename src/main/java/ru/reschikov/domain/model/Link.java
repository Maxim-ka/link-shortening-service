package ru.reschikov.domain.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Link  {

    private final String url;
    private final long timeCreated;
    private int limitFollow;

    @JsonCreator
    public Link(
            @JsonProperty("timeCreated") long timeCreated,
            @JsonProperty("limitFollow") int limitFollow,
            @JsonProperty("url") String url
    ) {
        this.timeCreated = timeCreated;
        this.limitFollow = limitFollow;
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setLimitFollow(int limitFollow) {
        this.limitFollow = limitFollow;
    }

    public int getLimitFollow() {
        return limitFollow;
    }

    public boolean hasLimitFollow() {
        return 0 < limitFollow;
    }

    public boolean isActive(long limit) {
        return System.currentTimeMillis() - timeCreated <= limit;
    }

    public int count() {
        this.limitFollow -= 1;
        return limitFollow;
    }
}
