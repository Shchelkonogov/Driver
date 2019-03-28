package ru.tecon.sessionBean.counterData;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.logging.Logger;

/**
 * Singleton bean Таймер, который грузит объекты,
 * которые надо проверять на данные
 */
@Startup
@Singleton
public class LoadSubsObjectsSBean {

    private static final Logger LOG = Logger.getLogger(LoadSubsObjectsSBean.class.getName());

    private static final String SQL_GET_OBJECTS = "select a.id, a.display_name from tsa_opc_object a " +
            "where opc_path like '%<Server>MCT-20</Server>' " +
            "and exists(select 1 from tsa_linked_object where opc_object_id = a.id and subscribed = 1)";

    @Resource(mappedName = "jdbc/OracleDataSource")
    private DataSource ds;

    @EJB
    private LoadObjectDataSBean bean;

    /**
     * Метод выгружает подписанные объекты и для каждого объекта
     * запускает асинхронный бин загрузки данных
     */
    @Schedule(minute="5/5", hour="*", persistent = false)
    private void timer() {
        LOG.info("LoadSubsObjectsSBean.timer check new data");

        try (Connection connect = ds.getConnection();
                PreparedStatement stmGetObjects = connect.prepareStatement(SQL_GET_OBJECTS)) {
            ResultSet res = stmGetObjects.executeQuery();
            while (res.next()) {
                bean.loadObjectParams(res.getString(1), res.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
