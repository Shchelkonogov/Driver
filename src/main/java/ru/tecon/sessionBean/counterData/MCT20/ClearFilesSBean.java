package ru.tecon.sessionBean.counterData.MCT20;

import ru.tecon.counter.MCT20.driver.Driver;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Singleton bean для чистки старых файлов
 */
@Startup
@Singleton
public class ClearFilesSBean {

    private static Logger log = Logger.getLogger(ClearFilesSBean.class.getName());

    /**
     * Метод в 5 минут каждого часа чистит все папки с файлами старше 45 дней
     */
    @Schedule(minute = "5", hour = "*", persistent = false)
    private void timer() {
        log.info("Timer clear files");

        LocalDateTime date = LocalDateTime.now().minusDays(45);

        Driver driver = new Driver();

        AtomicInteger counter = new AtomicInteger();
        for (String el: driver.getObjects().stream().map(e -> e.replace("МСТ-20-", "")).collect(Collectors.toList())) {
            counter.set(0);
            String filePath = driver.getUrl() + "/" + el.substring(0, 2) + "/" + el;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(filePath),
                    entry -> (entry.getFileName().toString().matches("ans-" + "\\d{8}-\\d{2}")
                            || entry.getFileName().toString().matches(el + "\\D\\d{8}-\\d{2}"))
                            && LocalDateTime.ofInstant(Files.readAttributes(entry, BasicFileAttributes.class).creationTime().toInstant(), ZoneId.systemDefault()).isBefore(date))) {
                stream.forEach(e -> {
                    try {
                        Files.delete(e);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    counter.getAndIncrement();
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

            log.info(counter.get() + " files remove from " + filePath);
        }
    }
}
