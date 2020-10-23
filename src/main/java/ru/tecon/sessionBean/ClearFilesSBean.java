package ru.tecon.sessionBean;

import ru.tecon.counter.Counter;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton bean отвечающий за чистку старых файлов
 */
@Startup
@Singleton
public class ClearFilesSBean {

    private static final Logger LOG = Logger.getLogger(ClearFilesSBean.class.getName());

    @EJB
    private AppConfigSBean appBean;

    /**
     * Метод в 0 часов 30 минут каждого дня чистит все папки с файлами
     */
    @Schedule(minute = "30", persistent = false)
    public void timer() {
        appBean.getServers().forEach(key -> {
            try {
                Counter cl = (Counter) Class.forName(appBean.get(key)).newInstance();
                cl.clear();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
                LOG.log(Level.WARNING, "error clear old files", ex);
            }
        });
    }
}
