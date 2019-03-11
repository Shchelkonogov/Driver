package ru.tecon.counter;

import ru.tecon.counter.model.DataModel;

import java.util.List;

/**
 * Интерфейс для все счетчиков
 */
public interface Counter {

    /**
     * Выгрузка агреса ftp
     * @return
     */
    String getURL();

    /**
     * Выгрузка данных
     * @param params параметры объекта для выгрузки данных
     * @param objectName имя объекта
     */
    void loadData(List<DataModel> params, String objectName);
}
