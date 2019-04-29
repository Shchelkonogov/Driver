package ru.tecon.sessionBean;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Stateless bean для загрузки информации о счетчиках в базу
 */
@Stateless
public class LoadOPCSBean {

    private final static Logger LOG = Logger.getLogger(LoadOPCSBean.class.getName());

    private static final String SQL_INSERT_OPC_OBJECT = "insert into tsa_opc_object values((select get_guid_base64 from dual), ?, ?, 1)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

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
                stmInsertObject.setString(2, "<OpcKind>Hda</OpcKind><ItemName>" + object + "</ItemName><Server>" + serverName + "</Server>");
                try {
                    LOG.info("LoadOPCSBean.insertOPCObjects Объект: " + object);
                    stmInsertObject.executeUpdate();
                    LOG.info("LoadOPCSBean.insertOPCObjects Успешная вставка");
                } catch(SQLException e) {
                    LOG.warning("LoadOPCSBean.insertOPCObjects Данная запись уже существует");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
