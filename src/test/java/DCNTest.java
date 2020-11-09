import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleDriver;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.*;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DCNTest {

    private static final List<String> SERVERS = Arrays.asList("МСТ-20", "МСТ-20-SA94", "МСТ-20-VIST",
            "МСТ-20-TEROS", "МСТ-20-SLAVE");

    private static Logger log = Logger.getLogger(DCNTest.class.getName());

    public static void main(String[] args) {
        DCNTest test = new DCNTest();
        test.run();
//        test.stop();
    }

    /**
     * Метод не убирает dcr запись из базы (select * from USER_CHANGE_NOTIFICATION_REGS)
     */
    private void run() {
        try (OracleConnection connect = connect()) {
            Properties prop = new Properties();
            prop.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
            prop.setProperty(OracleConnection.DCN_IGNORE_UPDATEOP, "true");
            prop.setProperty(OracleConnection.DCN_IGNORE_DELETEOP, "true");

            // большие ограничения по типам столбцов и операциям в sql
            prop.setProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, "true");

            // не понял как работает
//            prop.setProperty(OracleConnection.DCN_BEST_EFFORT, "true");

            DatabaseChangeRegistration dcr = connect.registerDatabaseChangeNotification(prop);

            dcr.addListener(System.out::println);

            Statement stm = connect.createStatement();
            ((OracleStatement) stm).setDatabaseChangeRegistration(dcr);

            StringJoiner joiner = new StringJoiner(", ", "(", ")");
            for (String name: SERVERS) {
                joiner.add("'" + name + "'");
            }

            stm.executeQuery("select server_name, kind from arm_tecon_commands " +
                    "where kind = 'AsyncRefresh' and server_name in " + joiner);

            for (String name: dcr.getTables()) {
                log.info(name + " is part of the registration.");
            }
        } catch (SQLException ex) {
            log.log(Level.WARNING, "error initialize dcn for opc config", ex);
        }
    }

    private void stop() {
        try (OracleConnection connect = connect()) {
            connect.unregisterDatabaseChangeNotification(1015206);
        } catch (SQLException ex) {
            log.log(Level.WARNING, "error unregister dcr", ex);
        }
    }

    private OracleConnection connect() throws SQLException {
        OracleDriver driver = new OracleDriver();
        Properties prop = new Properties();
        prop.setProperty("user", "admin");
        prop.setProperty("password", "vasilsursk");

        return (OracleConnection) driver.connect("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(LOAD_BALANCE=on)(ADDRESS=(PROTOCOL=TCP)(HOST=172.16.2.155)(PORT=1521))(ADDRESS=(PROTOCOL=TCP)(HOST=172.16.2.156)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=taf_nsi)))", prop);
    }
}
