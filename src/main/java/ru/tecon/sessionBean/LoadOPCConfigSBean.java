package ru.tecon.sessionBean;

import ru.tecon.counter.Counter;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Singleton bean Таймер, который выгружает конфу счетчиков по запросу
 */
@Startup
@Singleton
public class LoadOPCConfigSBean {

    private static final Logger LOG = Logger.getLogger(LoadOPCConfigSBean.class.getName());

    private static final String SQL_CHECK_REQUEST = "select id, args from arm_commands " +
            "where kind = 'ForceBrowse' and args like ? and is_success_execution is null";
    private static final String SQL_GET_OPC_OBJECT_ID = "select id, display_name from tsa_opc_object where opc_path = ?";
    private static final String SQL_INSERT_CONFIG = "insert into tsa_opc_element values ((select get_guid_base64 from dual), ?, ?, ?, 1, null)";
    private static final String SQL_UPDATE_CHECK = "update arm_commands " +
            "set is_success_execution = 1, result_description = ?, display_result_description = ?, end_time = sysdate where id = ?";

    @EJB
    private AppConfigSBean appBean;

    @Resource(mappedName = "jdbc/OracleDataSource")
    private DataSource ds;

    /**
     * Таймер, который кладет данные по конфигурации счетчиков,
     * если пришел запрос в базу на перечет конфигурации
     */
    @Schedule(second = "*/30", minute="*", hour="*", persistent = false)
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    private void timer() {
        LOG.info("LoadOPCConfigSBean.timer Check request");
        try (Connection connect = ds.getConnection();
                PreparedStatement stmCheck = connect.prepareStatement(SQL_CHECK_REQUEST);
                PreparedStatement stmObjectId = connect.prepareStatement(SQL_GET_OPC_OBJECT_ID);
                PreparedStatement stmUpdateConfig = connect.prepareStatement(SQL_INSERT_CONFIG);
                PreparedStatement stmUpdateCheck = connect.prepareStatement(SQL_UPDATE_CHECK)) {
            ResultSet resObjectId;

            for (String serverName: appBean.getServers()) {
                stmCheck.setString(1, "%<Server>" + serverName + "</Server>");

                ResultSet res = stmCheck.executeQuery();

                Counter counter = (Counter) Class.forName(appBean.get(serverName)).newInstance();

                List<String> config;

                while (res.next()) {
                    stmObjectId.setString(1, res.getString(2));
                    resObjectId = stmObjectId.executeQuery();
                    resObjectId.next();

                    config = counter.getConfig(resObjectId.getString(2));

                    int count = 0;
                    for (String item: config) {
                        stmUpdateConfig.setString(1, item);
                        stmUpdateConfig.setString(2, "<ItemName>" + resObjectId.getString(2) + ":" + item + "</ItemName>");
                        stmUpdateConfig.setString(3, resObjectId.getString(1));

                        try {
                            LOG.info("LoadOPCConfigSBean.timer Вставка записи " + item);
                            stmUpdateConfig.executeUpdate();
                            count++;
                            LOG.info("LoadOPCConfigSBean.timer Успешная вставка");
                        } catch (SQLException e) {
                            LOG.warning("LoadOPCConfigSBean.timer Запись уже существует");
                        }
                    }

                    stmUpdateCheck.setString(1, "<" + resObjectId.getString(2) + ">" + count + "</" + resObjectId.getString(2) + ">");
                    stmUpdateCheck.setString(2, "Получено " + count + " элементов по объекту '" + resObjectId.getString(2) + "'.");
                    stmUpdateCheck.setString(3, res.getString(1));

                    stmUpdateCheck.executeUpdate();
                }
            }
        } catch (SQLException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
