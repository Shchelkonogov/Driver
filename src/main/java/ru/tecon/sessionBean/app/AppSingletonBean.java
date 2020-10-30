package ru.tecon.sessionBean.app;

import ru.tecon.counter.util.ServerNames;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Singleton bean в котором хранится информация по драйверам
 */
@Startup
@Singleton
public class AppSingletonBean {

    private static final Map<String, String> countersMap = Stream.of(new String[][] {
//            {"IASDTU", "ru.tecon.counter.IASDTU.Driver"}})
            {ServerNames.MCT_20, "ru.tecon.counter.MCT20.Driver"},
            {ServerNames.MCT_20_SA94, "ru.tecon.counter.SA94.Driver"},
            {ServerNames.MCT_20_VIST, "ru.tecon.counter.VIST.Driver"},
            {ServerNames.MCT_20_TEROS, "ru.tecon.counter.TEROS.Driver"},
            {ServerNames.MCT_20_SLAVE, "ru.tecon.counter.MCT20_SLAVE.Driver"}})
            .collect(Collectors.toMap(data -> data[0], data -> data[1]));

    /**
     * Получение пути к драйверу
     * @param name имя драйвера
     * @return путь
     */
    public String get(String name) {
        return countersMap.get(name);
    }

    /**
     * Существует ли такой драйвер в списке
     * @param name имя драйвера
     * @return статус присутствия
     */
    public boolean containsKey(String name) {
        return countersMap.containsKey(name);
    }

    /**
     * Получение всех драйверов системы
     * @return список драйверов
     */
    public Set<String> getServers() {
        return countersMap.keySet();
    }
}
