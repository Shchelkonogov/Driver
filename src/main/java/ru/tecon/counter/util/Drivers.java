package ru.tecon.counter.util;

import ru.tecon.counter.Counter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Класс для работы с драйверами
 * Позволяет удалять старые файлы и обходить файловые пространства
 */
public class Drivers {

    private static Logger log = Logger.getLogger(Drivers.class.getName());

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HH");

    /**
    * Обход ftp
    * @param folder путь к ftp
    * @return список счетчиков
    */
    public static List<String> scan(String folder, List<String> fileRegex) {
        List<String> result = new ArrayList<>();
        search(result, folder, "\\d\\d", fileRegex);
        return result;
    }

    /**
     * Обход всех папок на ftp
     * @param result список счетчиков
     * @param folder путь для поиска
     * @param regex паттерн поиска имен папок
     * @param fileRegex паттерн для поиска файлов с определенным именем
     */
    private static void search(List<String> result, String folder, String regex, List<String> fileRegex) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(folder),
                entry -> entry.getFileName().toString().matches(regex))) {
            for (Path entry: stream) {
                if (Files.isDirectory(entry)) {
                    switch (entry.getFileName().toString().length()) {
                        case 2:
                            search(result, entry.toString(), entry.getFileName() + "\\d\\d", fileRegex);
                            break;
                        case 4:
                            try (Stream<Path> fileStream = Files.list(entry)
                                    .filter(entry1 -> {
                                        for (String fReg: fileRegex) {
                                            if (entry1.getFileName().toString().matches(fReg)) {
                                                return true;
                                            }
                                        }
                                        return false;
                                    })) {
                                if (fileStream.count() != 0) {
                                    result.add(entry.getFileName().toString());
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Удаление информации по данным которые уже устарели
     * @param counterClass драйвер
     * @param url путь к файлам
     * @param pattern паттерн поиска файлов
     */
    public static void clear(Counter counterClass, String url, List<String> pattern) {
        log.info("clear start");
        for (String el: counterClass.getObjects()) {
            AtomicInteger i = new AtomicInteger();
            String counter = el.substring(el.length() - 4);
            String filePath = url + "/" + counter.substring(0, 2) + "/" + counter;

            log.info("clear check path: " + filePath);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(filePath),
                    entry -> {
                        for (String p: pattern) {
                            if (entry.getFileName().toString().matches(p)) {
                                return true;
                            }
                        }
                        return false;
                    })) {
                List<FileData> fileData = new ArrayList<>();
                stream.forEach(path -> {
                    String fileName = path.getFileName().toString();
                    try {
                        LocalDateTime dateTimeTemp = LocalDateTime.parse(fileName.substring(fileName.length() - 11),
                                DATE_FORMAT);
                        fileData.add(new FileData(path, dateTimeTemp));
                    } catch(DateTimeParseException e) {
                        log.warning("clear date error " + fileName);
                    }
                });

                if (!fileData.isEmpty()) {
                    fileData.sort(Collections.reverseOrder());

                    LocalDateTime dateTime = fileData.get(0).getDateTime().minusDays(45);

                    fileData.forEach(file -> {
                        if (file.getDateTime().isBefore(dateTime)) {
                            try {
                                Files.delete(file.getPath());
                                i.getAndIncrement();
                            } catch (IOException ex) {
                                log.warning("clear error remove file " + file.getPath());
                            }
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("clear remove " + i + " files");
        }
        log.info("clear end");
    }

    /**
     * Метод возвращает список файлов для загрузки их информации в базу
     * @param filePath путь к папке
     * @param date дата начала
     * @param pattern паттерн поиска файлов
     * @return список файлов
     */
    public static List<FileData> getFilesForLoad(String filePath, LocalDateTime date, List<String> pattern) {
        List<FileData> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(filePath),
                entry -> {
                    for (String p: pattern) {
                        if (entry.getFileName().toString().matches(p)) {
                            try {
                                String fileName = entry.getFileName().toString();
                                LocalDateTime dateTimeTemp = LocalDateTime.parse(fileName.substring(fileName.length() - 11),
                                        DATE_FORMAT);
                                if (date != null) {
                                    return dateTimeTemp.isAfter(date);
                                } else {
                                    return true;
                                }
                            } catch(DateTimeParseException e) {
                                return false;
                            }
                        }
                    }
                    return false;
                })) {

            stream.forEach(path -> {
                String fileName = path.getFileName().toString();
                LocalDateTime dateTimeTemp = LocalDateTime.parse(fileName.substring(fileName.length() - 11),
                        DATE_FORMAT);
                result.add(new FileData(path, dateTimeTemp));
            });

            Collections.sort(result);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }
}
