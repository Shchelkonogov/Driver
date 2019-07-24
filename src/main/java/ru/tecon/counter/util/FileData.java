package ru.tecon.counter.util;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.StringJoiner;

public class FileData implements Serializable, Comparable<FileData> {

    private Path path;
    private LocalDateTime dateTime;

    FileData(Path path, LocalDateTime dateTime) {
        this.path = path;
        this.dateTime = dateTime;
    }

    public Path getPath() {
        return path;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    @Override
    public int compareTo(FileData o) {
        return dateTime.compareTo(o.dateTime);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", FileData.class.getSimpleName() + "[", "]")
                .add("path=" + path)
                .add("dateTime=" + dateTime)
                .toString();
    }
}
