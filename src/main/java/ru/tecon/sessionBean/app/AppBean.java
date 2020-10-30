package ru.tecon.sessionBean.app;

import javax.annotation.Resource;
import javax.ejb.EJBContext;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless bean для работы с приложением (универсальные методы, которые везде используются)
 */
@Stateless
public class AppBean {

    private static final Logger LOG = Logger.getLogger(AppBean.class.getName());

    private static final String UPDATE_ARM_TECON_COMMAND = "update arm_tecon_commands set is_success_execution = ?, " +
            "result_description = ?, end_time = sys_extract_utc(current_timestamp) where rowid = ?";

    private static final String UPDATE_ARM_COMMAND = "update arm_commands " +
            "set is_success_execution = ?, result_description = ?, display_result_description = ?, " +
            "end_time = sys_extract_utc(current_timestamp) where id = (select id from arm_tecon_commands where rowid = ?)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource
    private EJBContext context;

    /**
     * Метод обновляет статус выполнения запроса
     * @param status статус 0-ошибка, 1-выполнено
     * @param rowID rowID в таблице arm_tecon_commands
     * @param messageType тип сообщения
     * @param message сообщение
     * @return статус отработки метода
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean updateCommand(int status, String rowID, String messageType, String message) {
        try (Connection connection = ds.getConnection();
             PreparedStatement stmArmTecon = connection.prepareStatement(UPDATE_ARM_TECON_COMMAND);
             PreparedStatement stmArm = connection.prepareStatement(UPDATE_ARM_COMMAND)) {
            stmArmTecon.setInt(1, status);
            stmArmTecon.setString(2, message);
            stmArmTecon.setString(3, rowID);
            stmArmTecon.executeUpdate();

            stmArm.setInt(1, status);
            stmArm.setString(2, "<" + messageType + ">" + message + "</" + messageType + ">");
            stmArm.setString(3, message);
            stmArm.setString(4, rowID);
            stmArm.executeUpdate();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "error when update status", ex);
            context.setRollbackOnly();
            return false;
        }
        return true;
    }
}
