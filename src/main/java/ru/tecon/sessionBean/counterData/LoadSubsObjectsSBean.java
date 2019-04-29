package ru.tecon.sessionBean.counterData;

import ru.tecon.model.SubscriptionObject;
import ru.tecon.sessionBean.AppConfigSBean;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Singleton bean Таймер, который грузит объекты,
 * которые надо проверять на данные
 */
@Startup
@Singleton
public class LoadSubsObjectsSBean {

    private static final int partCount = 45;

    private static final Logger LOG = Logger.getLogger(LoadSubsObjectsSBean.class.getName());

    private static final String SQL_GET_OBJECTS = "select a.id, a.display_name from tsa_opc_object a " +
            "where opc_path like ? " +
            "and exists(select 1 from tsa_linked_object where opc_object_id = a.id and subscribed = 1)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @EJB
    private LoadObjectDataSBean bean;

    @EJB
    private AppConfigSBean appBean;

    /**
     * Метод выгружает подписанные объекты и для каждого объекта
     * запускает асинхронный бин загрузки данных
     */
    @Schedule(minute="5/15", hour="*", persistent = false)
    private void timer() {
        LOG.info("LoadSubsObjectsSBean.timer check new data");

        List<SubscriptionObject> objects = new ArrayList<>();

        try (Connection connect = ds.getConnection();
                PreparedStatement stmGetObjects = connect.prepareStatement(SQL_GET_OBJECTS)) {
            for (String serverName: appBean.getServers()) {
                stmGetObjects.setString(1, "%<Server>" + serverName + "</Server>");

                ResultSet res = stmGetObjects.executeQuery();
                while (res.next()) {
                    objects.add(new SubscriptionObject(res.getString(1), res.getString(2), serverName));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        int chunk = Math.max(objects.size() / partCount, 1);
        for (int i = 0; i < objects.size(); i += chunk) {
            bean.loadObjectParams(objects.subList(i, Math.min(objects.size(), i + chunk)));
        }
    }
}
