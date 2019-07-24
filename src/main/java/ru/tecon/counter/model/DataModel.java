package ru.tecon.counter.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DataModel implements Comparable<DataModel> {

    private String paramName;
    private int objectId;
    private int paramId;
    private int aggrId;
    private LocalDateTime startTime;
    private List<ValueModel> data = new ArrayList<>();

    public DataModel(String paramName, int objectId, int paramId, int aggrId, LocalDateTime startTime) {
        this.paramName = paramName;
        this.objectId = objectId;
        this.paramId = paramId;
        this.aggrId = aggrId;
        this.startTime = startTime;
    }

    public void addData(ValueModel item) {
        data.add(item);
    }

    public String getParamName() {
        return paramName;
    }

    public int getObjectId() {
        return objectId;
    }

    public int getParamId() {
        return paramId;
    }

    public int getAggrId() {
        return aggrId;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public List<ValueModel> getData() {
        return data;
    }

    @Override
    public String toString() {
        return "DataModel{" + "paramName='" + paramName + '\'' +
                ", objectId=" + objectId +
                ", paramId=" + paramId +
                ", aggrId=" + aggrId +
                ", startTime=" + startTime +
                ", data=" + data +
                '}';
    }

    @Override
    public int compareTo(DataModel o) {
        if (o.startTime == null) {
            return 1;
        }
        if (startTime == null) {
            return -1;
        }
        return startTime.compareTo(o.startTime);
    }
}
