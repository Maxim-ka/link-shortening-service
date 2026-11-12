package ru.reschikov;

import ru.reschikov.data.StorageProvider;
import ru.reschikov.data.Storage;
import ru.reschikov.presentation.Controller;
import ru.reschikov.presentation.ControllerImpl;
import ru.reschikov.usecase.ServiceImp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

//TIP Для <b>запуска</b> кода нажмите <shortcut actionId="Run"/> или
// щелкните значок <icon src="AllIcons.Actions.Execute"/> в боковой области.
public class Main {

    public static final String PATTERN_OUT = "^[qQйЙ]$";
    private static final String PATTERN_HELP = "^[ПпgG]$";
    private static final String SEPARATOR = "\\s+";
    private static final int TIMEOUT_AWAIT_OUT = 10;
    private static final ExecutorService singleExecutor = Executors.newSingleThreadExecutor();



    private static volatile String input = "";

    public static void main(String[] args) {
        Storage storage = new StorageProvider();
        Controller controller = new ControllerImpl(new ServiceImp(storage), storage, singleExecutor);
        Scanner scanner = new Scanner(System.in);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!input.matches(PATTERN_OUT)) {
                try {
                    controller.close();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            singleExecutor.shutdown();

            try {
                if (!singleExecutor.awaitTermination(TIMEOUT_AWAIT_OUT, TimeUnit.SECONDS)) {
                    singleExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                singleExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));

        try(controller) {
            do {
                System.out.println("Выход-Q, помощь-П");
                System.out.println("Создание короткой ссылки: 'логин(если есть) ссылка лимит'");
                System.out.println("Переход по короткой ссылке: 'логин ссылка'");
                input = scanner.nextLine().trim();
                if(input.matches(PATTERN_OUT)) break;
                if (input.matches(PATTERN_HELP)) {
                    Help.upload();
                } else {
                    String[] strings = input.split(SEPARATOR);
                    String answer = controller.take(strings);
                    if (answer != null) {
                        System.out.println(answer);
                    }
                }
                System.out.println();
            } while (!input.matches(PATTERN_OUT));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        scanner.close();
        System.out.println("Завершение работы");

    }

    private static class Help {

        private static final String PATH_HELP = "help.txt";

        private static void upload() {
            InputStream is = Main.class.getClassLoader().getResourceAsStream(PATH_HELP);
            try (is) {
                if (is == null) {
                    System.out.println("Файл помощи отсутствует");
                    return;
                }
                try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                ) {
                    reader.lines().forEach(System.out::println);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}

