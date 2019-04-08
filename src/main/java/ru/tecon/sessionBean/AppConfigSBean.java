package ru.tecon.sessionBean;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Startup
@Singleton
public class AppConfigSBean {

    private static final Map<String, String> countersMap = Stream.of(new String[][] {
            {"MCT-20", "ru.tecon.counter.MCT20.driver.Driver"},
            {"IASDTU", "ru.tecon.counter.IASDTU.Driver"}})
            .collect(Collectors.toMap(data -> data[0], data -> data[1]));

    public String get(String name) {
        return countersMap.get(name);
    }

    public boolean containsKey(String name) {
        return countersMap.containsKey(name);
    }

    public Set<String> getServers() {
        return countersMap.keySet();
    }
}
