package ru.tecon.sessionBean.counterData;

import ru.tecon.counter.Counter;
import ru.tecon.sessionBean.app.AppSingletonBean;
import ru.tecon.sessionBean.model.SubscriptionObject;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton bean для работы с архивными данными
 */
@Startup
@Singleton
public class CounterDataSingletonBean {

    private static final Logger LOG = Logger.getLogger(CounterDataSingletonBean.class.getName());

    private static final int partCount = 45;

    @EJB
    private AppSingletonBean appSingletonBean;

    @EJB
    private CounterDataBean counterDataBean;

    /**
     * Метод в 0 часов 30 минут каждого дня чистит все папки с файлами
     */
    @Schedule(minute = "30", persistent = false)
    private void clearFiles() {
        for (String server : appSingletonBean.getServers()) {
            try {
                Counter counter = (Counter) Class.forName(appSingletonBean.get(server)).newInstance();
                counter.clearHistorical();
                counter.clearAlarms();
            } catch (IllegalAccessException | InstantiationException | ClassNotFoundException ex) {
                LOG.log(Level.WARNING, "error clearHistorical old files", ex);
            }
        }
    }

    /**
     * Метод инициирует разбор архивных файлов с данными
     */
    @Schedule(minute = "10/15", hour = "*", persistent = false)
    private void readHistoricalFiles() {
        List<SubscriptionObject> objects = counterDataBean.getSubscribedObjects();

        int chunk = Math.max(objects.size() / partCount, 1);
        for (int i = 0; i < objects.size(); i += chunk) {
            counterDataBean.initReadHistoricalFiles(objects.subList(i, Math.min(objects.size(), i + chunk)));
        }
    }

    /**
     * Метод инициирует разбор файлов с тревогами
     */
    @Schedule(minute="*/5", hour="*", persistent = false)
    private void readAlarmFiles() {
        List<SubscriptionObject> objects = counterDataBean.getSubscribedObjects();

        int chunk = Math.max(objects.size() / partCount, 1);
        for (int i = 0; i < objects.size(); i += chunk) {
            counterDataBean.initReadAlarmsFiles(objects.subList(i, Math.min(objects.size(), i + chunk)));
        }
    }
}
