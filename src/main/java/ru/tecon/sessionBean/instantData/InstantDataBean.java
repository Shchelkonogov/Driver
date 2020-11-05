package ru.tecon.sessionBean.instantData;

import ru.tecon.counter.Counter;
import ru.tecon.counter.exception.DriverDataLoadException;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;
import ru.tecon.counter.util.ServerNames;
import ru.tecon.sessionBean.app.AppBean;
import ru.tecon.sessionBean.app.AppSingletonBean;
import ru.tecon.sessionBean.counterData.CounterDataBean;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless bean для работы с мгновенными значениями
 */
@Stateless
public class InstantDataBean {

    private static final Logger LOG = Logger.getLogger(InstantDataBean.class.getName());

    private static final Pattern PATTERN = Pattern.compile(".*-(?<item>\\d{4})$");

    private static final String SQL_GET_ASYNC_REQUEST = "select opc_id, server_name, obj_name, kind " +
            "from arm_tecon_commands where rowid = ?";

    private static final String SQL_GET_LINKED_PARAMETERS_FOR_INSTANT_DATA = "select c.display_name || " +
            "nvl2(extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), " +
            "'::' || extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), ''), " +
            "b.aspid_object_id, b.aspid_param_id, b.aspid_agr_id, b.measure_unit_transformer " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element where opc_object_id = ?) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a " +
            "where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
            "and b.aspid_agr_id is null";

    private static final String INSERT_ASYNC_REFRESH_DATA = "insert into arm_async_refresh_data " +
            "values (?, ?, sys_extract_utc(current_timestamp), ?, ?, " +
            "(select id from arm_tecon_commands where rowid = ?), current_timestamp, " +
            "(select extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/ItemName') " +
            "from tsa_opc_element where id in (select opc_element_id from tsa_linked_element " +
            "where aspid_object_id = ? and aspid_param_id = ? and aspid_agr_id is null)), " +
            "null)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @EJB
    private InstantDataBean instantDataBean;

    @EJB
    private AppSingletonBean appSingletonBean;

    @EJB
    private AppBean appBean;

    @EJB
    private CounterDataBean counterDataBean;

    /**
     * Асинхронный метод для запуска получения мгновенных данных
     * @param rowID rowID в таблице arm_tecon_commands для которой грузим мгновенные данные
     */
    @Asynchronous
    public void initLoadInstantData(String rowID) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_GET_ASYNC_REQUEST)) {
            stm.setString(1, rowID);

            String serverName = null;
            String item = null;
            String opcObjectID = null;
            String objectName = null;

            ResultSet res = stm.executeQuery();
            if (res.next()) {
                if (!res.getString(4).equals("AsyncRefresh")) {
                    return;
                }

                if (ServerNames.SERVERS.contains(res.getString(2))) {
                    serverName = res.getString(2);
                } else {
                    return;
                }

                Matcher m = PATTERN.matcher(res.getString(3));
                if (m.find()) {
                    item = m.group("item");
                } else {
                    return;
                }

                opcObjectID = res.getString(1);
                objectName = res.getString(3);
            }

            if ((serverName == null) || (item == null)) {
                return;
            }

            List<DataModel> parameters = instantDataBean.loadObjectInstantParameters(opcObjectID);

            if (!parameters.isEmpty()) {
                try {
                    Counter cl = (Counter) Class.forName(appSingletonBean.get(serverName)).newInstance();
                    cl.loadInstantData(parameters, item);
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
                    LOG.log(Level.WARNING, "server error", ex);
                    appBean.updateCommand(0, rowID, "Error", "Ошибка сервиса получения данных");
                    return;
                } catch (DriverDataLoadException ex) {
                    LOG.log(Level.WARNING, "instantDataException", ex);
                    appBean.updateCommand(0, rowID, "Error", ex.getMessage());
                    return;
                }
            } else {
                appBean.updateCommand(0, rowID, "Error", "Нет мгновенных параметров");
                return;
            }

            parameters.removeIf(dataModel -> dataModel.getData().isEmpty());

            int count = instantDataBean.putInstantData(rowID, parameters);
            counterDataBean.putData(parameters);

            if (count != -1) {
                appBean.updateCommand(1, rowID, objectName,
                        "Получено " + count + " значений из " + parameters.size() + " слинкованных параетров по объекту " + objectName);
            } else {
                appBean.updateCommand(0, rowID, "Error", "Ошибка сервиса загрузки данных");
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Ошибка обработки запроса", ex);
        }
    }

    /**
     * Метод выгружает список параметров мгновенных данных
     * @param opcObjectID id opc объекта
     * @return список параметров
     */
    public ArrayList<DataModel> loadObjectInstantParameters(String opcObjectID) {
        ArrayList<DataModel> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_GET_LINKED_PARAMETERS_FOR_INSTANT_DATA)) {
            stm.setString(1, opcObjectID);
            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(new DataModel(res.getString(1), res.getInt(2), res.getInt(3), res.getInt(4),
                        null, (res.getString(5) == null) ? null : res.getString(5).substring(2)));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "error while load instant data parameters for opc object " + opcObjectID, e);
        }
        return result;
    }

    /**
     * Метод выгружает в базу мгновенные данные
     * @param rowID rowID в таблице arm_tecon_commands
     * @param paramList список параметров
     * @return статус выполнения метода
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int putInstantData(String rowID, List<DataModel> paramList) {
        int[] size;
        try (Connection connection = ds.getConnection();
             PreparedStatement stmInsert = connection.prepareStatement(INSERT_ASYNC_REFRESH_DATA)) {
            Integer objectID = null;

            for (DataModel model: paramList) {
                objectID = model.getObjectId();
                for (ValueModel valueModel: model.getData()) {
                    stmInsert.setInt(1, objectID);
                    stmInsert.setString(2, valueModel.getValue());
                    stmInsert.setInt(3, valueModel.getQuality());
                    stmInsert.setInt(4, model.getParamId());
                    stmInsert.setString(5, rowID);
                    stmInsert.setInt(6, objectID);
                    stmInsert.setInt(7, model.getParamId());

                    stmInsert.addBatch();
                }
            }

            size = stmInsert.executeBatch();

            LOG.info("Вставил " + size.length + " мгновенных значений по объекту " + objectID +
                    ". rowID запроса в таблице arm_tecon_commands " + rowID);
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Ошибка импорта мгновенных данных в базу", ex);
            return -1;
        }

        return size.length;
    }
}
