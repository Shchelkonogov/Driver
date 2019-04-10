package ru.tecon.counter.IASDTU.sessionBean;

import ru.tecon.counter.model.DataModel;

import javax.ejb.Local;
import java.util.List;

/**
 * Local интерфейс для выгрузки данных из ms sql server
 */
@Local
public interface TransferBLocal {

    /**
     * Выгрузка opc объектов
     * @return список объектов
     */
    List<String> getObjects();

    /**
     * Выгрузка парамтеров конфигурации объекта
     * @param object имя объекта
     * @return список параметров
     */
    List<String> getConfig(String object);

    /**
     * Выгрузка данных по параметрам
     * @param params список параметров
     * @param objectName имя объекта
     */
    void loadData(List<DataModel> params, String objectName);
}
