package ru.tecon.sessionBean.alarm;

import ru.tecon.alarm.Clear;
import ru.tecon.model.SubscriptionObject;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@Singleton
public class AlarmSBean {

    private static Logger log = Logger.getLogger(AlarmSBean.class.getName());

    private static final int partCount = 45;
    private static final String SERVER_NAME = "MCT-20";

    private static final String SQL_GET_OBJECTS = "select a.id, a.display_name from tsa_opc_object a " +
            "where opc_path like '%<Server>" + SERVER_NAME + "</Server>' " +
            "and exists(select 1 from tsa_linked_object where opc_object_id = a.id and subscribed = 1)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource(name = "url")
    private String url;

    @EJB
    private LoadParamsSBean bean;

    @Schedule(minute="*/5", hour="*", persistent = false)
    private void timer() {
        log.info("Check for new alarms");

        List<SubscriptionObject> objects = new ArrayList<>();

        try (Connection connect = ds.getConnection();
             PreparedStatement stmGetObjects = connect.prepareStatement(SQL_GET_OBJECTS)) {
            ResultSet res = stmGetObjects.executeQuery();
            while (res.next()) {
                objects.add(new SubscriptionObject(res.getString(1), res.getString(2), SERVER_NAME));
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "Error when get subscription objects", e);
        }

        int chunk = Math.max(objects.size() / partCount, 1);
        for (int i = 0; i < objects.size(); i += chunk) {
            bean.loadObjectParams(objects.subList(i, Math.min(objects.size(), i + chunk)));
        }
        log.info("End check alarms");
    }

    /**
     * Таймер который срабатывает в 0 часов 30 минут каждого дня.
     * Удаляет alarm файлы старше 45 дней
     */
    @Schedule(minute = "30", persistent = false)
    private void clearAlarms() {
        Clear.clear(url + "/alarms");
    }
}
