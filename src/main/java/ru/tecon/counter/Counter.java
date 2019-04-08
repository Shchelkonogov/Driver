package ru.tecon.counter;

import ru.tecon.counter.model.DataModel;

import java.util.List;

/**
 * Интерфейс для все счетчиков
 */
public interface Counter {

    /**
     * Выгрузка opc объектов
     * @return список объектов
     */
    List<String> getObjects();

    /**
     * Выгрузка конфигурации объекта
     * @param object имя объекта
     * @return список с конфигурацией
     */
    List<String> getConfig(String object);

    /**
     * Выгрузка данных
     * @param params параметры объекта для выгрузки данных
     * @param objectName имя объекта
     */
    void loadData(List<DataModel> params, String objectName);
}
