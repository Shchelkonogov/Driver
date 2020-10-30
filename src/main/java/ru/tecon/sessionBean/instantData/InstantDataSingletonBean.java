package ru.tecon.sessionBean.instantData;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import oracle.jdbc.dcn.QueryChangeDescription;
import oracle.jdbc.dcn.RowChangeDescription;
import oracle.jdbc.dcn.TableChangeDescription;
import ru.tecon.counter.util.ServerNames;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton метод, для инициализации работы с мгновенными данными
 */
@Startup
@Singleton
public class InstantDataSingletonBean {

    private static final Logger LOG = Logger.getLogger(InstantDataSingletonBean.class.getName());

    private DatabaseChangeRegistration dcr;

    private static final String DELETE_INSTANT_DATA_REQUEST = "delete from arm_tecon_commands " +
            "where kind = 'AsyncRefresh' and server_name not in (select * from table(?))";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @EJB
    private InstantDataBean instantData;

    /**
     * метод инициализирует dcn подписку на таблицу запросов на мгновенные данные
     */
    @PostConstruct
    private void init() {
        try (OracleConnection connect = (OracleConnection) ds.getConnection()) {
            Properties prop = new Properties();
            prop.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
            prop.setProperty(OracleConnection.DCN_IGNORE_DELETEOP, "true");
            prop.setProperty(OracleConnection.DCN_IGNORE_UPDATEOP, "true");
            prop.setProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, "true");

            dcr = connect.registerDatabaseChangeNotification(prop);

            dcr.addListener(e -> {
                for (QueryChangeDescription queryDescription: e.getQueryChangeDescription()) {
                    for (TableChangeDescription tableDescription: queryDescription.getTableChangeDescription()) {
                        if (tableDescription.getTableName().equals("ADMIN.ARM_TECON_COMMANDS")) {
                            for (RowChangeDescription rowDescription: tableDescription.getRowChangeDescription()) {
                                if (rowDescription.getRowOperation() == RowChangeDescription.RowOperation.INSERT) {
                                    LOG.info("instant data load request: " + rowDescription.getRowid());
                                    instantData.initLoadInstantData(rowDescription.getRowid().stringValue());
                                }
                            }
                        }
                    }
                }
            });

            try (Statement stm = connect.createStatement()) {
                ((OracleStatement) stm).setDatabaseChangeRegistration(dcr);

                StringJoiner joiner = new StringJoiner(", ", "(", ")");
                for (String name: ServerNames.SERVERS) {
                    joiner.add("'" + name + "'");
                }

                stm.executeQuery("select server_name, kind from arm_tecon_commands " +
                        "where kind = 'AsyncRefresh' and server_name in " + joiner);

                for (String name: dcr.getTables()) {
                    LOG.info(name + " is part of the registration.");
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "error initialize dcn for instant data", ex);
        }
    }

    /**
     * Метод срабатывает каждый день в 0 часов 5 минут и чистит таблицу с запросами мгновенных данных
     */
    @Schedule(minute = "5", persistent = false)
    private void clearTable() {
        try (OracleConnection connect = (OracleConnection) ds.getConnection();
             PreparedStatement statement = connect.prepareStatement(DELETE_INSTANT_DATA_REQUEST)) {
            statement.setArray(1, connect.createOracleArray("T_VARCHAR_250", ServerNames.SERVERS.toArray()));
            statement.executeQuery();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "error remove data from arm_tecon_commands", ex);
        }
    }

    /**
     * Метод убирает dcn подписку на таблицу мгновенных данных
     */
    @PreDestroy
    private void destroy() {
        try (OracleConnection conn = (OracleConnection) ds.getConnection()) {
            conn.unregisterDatabaseChangeNotification(dcr);

            LOG.info("unregister dcn form instant data");
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "error unregister dcn form instant data", ex);
        }
    }
}
