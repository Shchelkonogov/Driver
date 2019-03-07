package ru.tecon.counter.model;

import java.time.LocalDateTime;

public class ValueModel {

    private String value;
    private LocalDateTime time;

    public ValueModel(String value, LocalDateTime time) {
        this.value = value;
        this.time = time;
    }

    public String getValue() {
        return value;
    }

    public LocalDateTime getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "ValueModel{" + "value='" + value + '\'' +
                ", time=" + time +
                '}';
    }
}
