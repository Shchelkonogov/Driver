package ru.tecon.counter;

import ru.tecon.counter.exception.DriverDataLoadException;
import ru.tecon.counter.model.DataModel;

import java.util.List;

/**
 * Интерфейс для все счетчиков
 */
public abstract class Counter {

    /**
     * Выгрузка opc объектов
     * @return список объектов
     */
    public abstract List<String> getObjects();

    /**
     * Выгрузка конфигурации объекта
     * @param object имя объекта
     * @return список с конфигурацией
     */
    public abstract List<String> getConfig(String object);

    /**
     * Выгрузка данных
     * @param params параметры объекта для выгрузки данных
     * @param objectName имя объекта
     */
    public abstract void loadData(List<DataModel> params, String objectName);

    /**
     * Выгрузка оповещений по ошибкам
     * @param params параметры объекта для выгрузки данных
     * @param objectName имя объекта
     */
    public void loadAlarmData(List<DataModel> params, String objectName) {
    }

    /**
     * выгрузка мгновенных данных
     * @param params параметры объекта для выгрузки данных
     * @param counterNumber номер счетчика
     */
    public void loadInstantData(List<DataModel> params, String counterNumber) throws DriverDataLoadException {
    }

    /**
     * Удаление старой информации из хранилища
     */
    public void clearHistorical() {
    }

    /**
     * Удаление старой информации из хранилища
     */
    public void clearAlarms() {
    }
}
