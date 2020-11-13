package ru.tecon.sessionBean.app;

import ru.tecon.counter.Counter;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.*;

/**
 * Singleton bean в котором хранится информация по драйверам
 */
@Startup
@Singleton
public class AppSingletonBean {

    private static final List<String> DRIVERS = Arrays.asList(
//            "ru.tecon.counter.counterImpl.IASDTU.Driver",
            "ru.tecon.counter.counterImpl.MCT20.Driver",
            "ru.tecon.counter.counterImpl.SA94.Driver",
            "ru.tecon.counter.counterImpl.VIST.Driver",
            "ru.tecon.counter.counterImpl.TEROS.Driver",
            "ru.tecon.counter.counterImpl.MCT20_SLAVE.Driver"
            );

    private static final Map<String, String> COUNTERS_MAP = new HashMap<>();

    @PostConstruct
    private void init() {
        try {
            for (String driver : DRIVERS) {
                Counter counter = (Counter) Class.forName(driver).newInstance();
                COUNTERS_MAP.put(counter.getServerName(), driver);
            }
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Получение пути к драйверу
     * @param name имя драйвера
     * @return путь
     */
    public String get(String name) {
        return COUNTERS_MAP.get(name);
    }

    /**
     * Существует ли такой драйвер в списке
     * @param name имя драйвера
     * @return статус присутствия
     */
    public boolean containsKey(String name) {
        return COUNTERS_MAP.containsKey(name);
    }

    /**
     * Получение всех драйверов системы
     * @return список драйверов
     */
    public Set<String> getServers() {
        return COUNTERS_MAP.keySet();
    }
}
