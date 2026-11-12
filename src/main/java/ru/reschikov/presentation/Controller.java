package ru.reschikov.presentation;

public interface Controller extends AutoCloseable {

    String take(String[] strings) throws Exception;
}
