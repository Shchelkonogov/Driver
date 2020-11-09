package ru.tecon.sessionBean.opcObjects;

import ru.tecon.counter.Counter;
import ru.tecon.sessionBean.app.AppBean;
import ru.tecon.sessionBean.app.AppSingletonBean;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless bean для загрузки информации о счетчиках в базу
 */
@Stateless
public class OpcObjectBean {

    private final static Logger LOG = Logger.getLogger(OpcObjectBean.class.getName());

    private static final String SQL_INSERT_OPC_OBJECT = "insert into tsa_opc_object values((select get_guid_base64 from dual), ?, ?, 1)";

    private static final String SQL_GET_FORCE_BROWSE_REQUEST = "select opc_id, server_name, obj_name, kind " +
            "from arm_tecon_commands where rowid = ?";

    private static final String INSERT_CONFIG = "insert into tsa_opc_element " +
            "values ((select get_guid_base64 from dual), ?, ?, ?, 1, null)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @EJB
    private AppSingletonBean appSingletonBean;

    @EJB
    private AppBean appBean;

    @EJB
    private OpcObjectBean opcObjectBean;

    /**
     * Асинхронный метод для запуска получения конфигурации объекта
     * @param rowID rowID в таблице arm_tecon_commands для которой грузим конфигурацию объекта
     */
    @Asynchronous
    public void initLoadOPCConfig(String rowID) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_GET_FORCE_BROWSE_REQUEST)) {
            stm.setString(1, rowID);

            String serverName = null;
            String objectName = null;
            String opcObjectID = null;

            ResultSet res = stm.executeQuery();
            if (res.next()) {
                if (!res.getString(4).equals("ForceBrowse")) {
                    return;
                }

                if (appSingletonBean.containsKey(res.getString(2))) {
                    serverName = res.getString(2);
                } else {
                    return;
                }

                objectName = res.getString(3);
                opcObjectID = res.getString(1);
            }

            if ((serverName == null)) {
                return;
            }

            List<String> config;

            try {
                Counter cl = (Counter) Class.forName(appSingletonBean.get(serverName)).newInstance();
                config = cl.getConfig(objectName);
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
                LOG.log(Level.WARNING, "server error", ex);
                appBean.updateCommand(0, rowID, "Error", "Ошибка сервиса получения данных");
                return;
            }

            if (!config.isEmpty()) {
                int result = opcObjectBean.insertConfig(config, opcObjectID, objectName);
                if (result < 0) {
                    appBean.updateCommand(0, rowID, "Error", "Ошибка сервиса загрузки данных");
                } else {
                    appBean.updateCommand(1, rowID, objectName,
                            "Получено " + result + " новых элементов по объекту '" + objectName + "'.");
                }
            } else {
                LOG.warning("Конфигурация отсутствует");
                appBean.updateCommand(0, rowID, "Error", "Конфигурация отсутствует");
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Ошибка обработки запроса", ex);
        }
    }

    /**
     * Метод кладет в базу конфигурацию объекта
     * @param config список параметров конфигурации
     * @param opcObjectID id opc объекта
     * @param objectName имя объекта
     * @return возвращает количество вставленных параметров,
     * -1 если произошла ошибка при вставки
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int insertConfig(List<String> config, String opcObjectID, String objectName) {
        int count = 0;
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(INSERT_CONFIG)) {
            for (String item: config) {
                stm.setString(1, item);
                stm.setString(2, "<ItemName>" + objectName + ":" + item + "</ItemName>");
                stm.setString(3, opcObjectID);

                try {
                    stm.executeUpdate();
                    count++;
                    LOG.info("Успешная вставка параметра \"" + item + "\"");
                } catch (SQLException ex) {
                    LOG.warning("Параметр \"" + item + "\" уже существует");
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Error upload config", ex);
            return -1;
        }
        return count;
    }

    /**
     * Загрузка всех номеров счетчиков в базу
     * @param objects список счетчиков
     * @param serverName имя opc сервера
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void insertOPCObjects(List<String> objects, String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stmInsertObject = connect.prepareStatement(SQL_INSERT_OPC_OBJECT)) {
            for (String object: objects) {
                stmInsertObject.setString(1, object);
                stmInsertObject.setString(2, "<OpcKind>Hda</OpcKind><ItemName>" + object + "</ItemName>" +
                        "<Server>" + serverName + "</Server>");
                try {
                    stmInsertObject.executeUpdate();
                    LOG.info("Добавлен новый объект: " + object);
                } catch(SQLException ex) {
                    LOG.log(Level.WARNING, "Ошибка записи объекта " + object, ex);
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Ошибка загрузки объектов", ex);
        }
    }
}
