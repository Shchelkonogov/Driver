package ru.tecon.alarm;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Clear {

    private static Logger log = Logger.getLogger(Clear.class.getName());

    public static void clear(String alarmPath) {
        log.info("Start clear alarms");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(alarmPath),
                entry -> entry.getFileName().toString().matches("\\d{4}a\\d{8}-\\d{6}"))) {
            stream.forEach(path -> {
                if (LocalDateTime.parse(path.getFileName().toString().substring(5), DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                        .isBefore(LocalDateTime.now().minusDays(45))) {
                    try {
                        Files.delete(path);
                        log.info("delete ok: " + path.getFileName().toString());
                    } catch (IOException e) {
                        log.log(Level.WARNING, "error delete file", e);
                    }
                }
            });
        } catch (IOException e) {
            log.log(Level.WARNING, "error when clear alarms", e);
        }
        log.info("Stop clear alarms");
    }
}
