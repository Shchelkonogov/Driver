package ru.tecon.sessionBean;

import ru.tecon.counter.Counter;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;

@Startup
@Singleton
public class ClearFilesSBean {

    @EJB
    private AppConfigSBean appBean;

    /**
     * Метод в 0 часов 30 минут каждого дня чистит все папки с файлами старше 45 дней
     */
    @Schedule(minute = "30", persistent = false)
    public void timer() {
        appBean.getServers().forEach(key -> {
            try {
                Counter cl = (Counter) Class.forName(appBean.get(key)).newInstance();
                cl.clear();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        });
    }
}
