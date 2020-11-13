package ru.tecon.counter.util;

import ru.tecon.counter.Counter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Класс для работы с драйверами
 * Позволяет удалять старые файлы и обходить файловые пространства
 */
public class Drivers {

    private static Logger log = Logger.getLogger(Drivers.class.getName());

    private static int[] table = {
            0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
            0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
            0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
            0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
            0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
            0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
            0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
            0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
            0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
            0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
            0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
            0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
            0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
            0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
            0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
            0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
            0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
            0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
            0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
            0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
            0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
            0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
            0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
            0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
            0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
            0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
            0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
            0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
            0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
            0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
            0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
            0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
    };

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

        List<String> patternWrap = new ArrayList<>(pattern);
        patternWrap.addAll(pattern.stream().map(s -> s + "-er").collect(Collectors.toList()));

        for (String el: counterClass.getObjects()) {
            AtomicInteger i = new AtomicInteger();
            String counter = el.substring(el.length() - 4);
            String filePath = url + "/" + counter.substring(0, 2) + "/" + counter;

            log.info("clear check path: " + filePath);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(filePath),
                    entry -> {
                        for (String p: patternWrap) {
                            if (entry.getFileName().toString().matches(p)) {
                                return true;
                            }
                        }
                        return false;
                    })) {
                stream.forEach(path -> {
                    try {
                        FileTime creationTime = (FileTime) Files.getAttribute(path, "creationTime");

                        if (LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault())
                                .isBefore(LocalDateTime.now().minusDays(45))) {
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        log.warning("error delete file " + path.toString());
                    }
                });
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
        return getFilesForLoad(filePath, date, pattern, "yyyyMMdd-HH");
    }

    /**
     * Метод возвращает список файлов для загрузки их информации в базу
     * @param filePath путь к папке
     * @param date дата начала
     * @param pattern паттерн поиска файлов
     * @param dateFormat формат для разбора даты в имени файла
     * @return список файлов
     */
    public static List<FileData> getFilesForLoad(String filePath, LocalDateTime date, List<String> pattern,
                                                 String dateFormat) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);

        List<FileData> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(filePath),
                entry -> {
                    for (String p: pattern) {
                        if (entry.getFileName().toString().matches(p)) {
                            try {
                                String fileName = entry.getFileName().toString();
                                LocalDateTime dateTimeTemp = LocalDateTime.parse(fileName.substring(fileName.length() - dateFormat.length()),
                                        formatter);
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
                LocalDateTime dateTimeTemp = LocalDateTime.parse(fileName.substring(fileName.length() - dateFormat.length()),
                        formatter);
                result.add(new FileData(path, dateTimeTemp));
            });

            Collections.sort(result);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Алгоритм расчета crc-16/modbus
     * @param data массив байтов
     * @return crc-16/modbus
     */
    public static Integer computeCrc16(byte[] data) {
        int crc = 0xffff;
        for (byte b : data) {
            crc = (crc >>> 8) ^ table[(crc ^ b) & 0xff];
        }

        return crc;
    }

    public static void markFileError(Path path) throws IOException {
        FileTime creationTime = (FileTime) Files.getAttribute(path, "creationTime");

        if (LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault())
                .isBefore(LocalDateTime.now().minusHours(24))) {
            Files.move(path, Paths.get(path.toString() + "-er"));
        }
    }
}
