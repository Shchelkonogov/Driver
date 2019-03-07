package ru.tecon.counter;

import ru.tecon.counter.model.DataModel;

import java.util.List;

public interface Counter {

    String getURL();

    void loadData(List<DataModel> params, String objectName);
}
