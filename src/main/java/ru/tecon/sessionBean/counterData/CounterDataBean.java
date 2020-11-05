package ru.tecon.sessionBean.counterData;

import oracle.jdbc.OracleConnection;
import ru.tecon.counter.Counter;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;
import ru.tecon.sessionBean.app.AppSingletonBean;
import ru.tecon.sessionBean.model.SubscriptionObject;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless bean для работы с архивными данными
 */
@Stateless
public class CounterDataBean {

    private static final Logger LOG = Logger.getLogger(CounterDataBean.class.getName());

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH");

    private static final String SELECT_LINKED_PARAMETERS = "select c.display_name || " +
                "nvl2(extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), " +
                    "'::' || extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), ''), " +
            "b.aspid_object_id, b.aspid_param_id, b.aspid_agr_id, b.measure_unit_transformer " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element where opc_object_id = ?) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a " +
                        "where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
            "and b.aspid_agr_id is not null";

    private static final String SELECT_START_DATE = "select to_char(time_stamp, 'dd.mm.yyyy hh24') " +
            "from dz_input_start where obj_id = ? and par_id = ? and stat_aggr = ?";

    private static final String INSERT_DATA = "{call dz_util1.input_data(?)}";

    private static final String SELECT_SUBSCRIBED_OBJECTS = "select a.id, a.display_name from tsa_opc_object a where opc_path like ? " +
            "and exists(select 1 from tsa_linked_object where opc_object_id = a.id and subscribed = 1)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource(name = "jdbc/DataSourceFil")
    private DataSource dsFil;

    @EJB
    private AppSingletonBean appSingletonBean;

    @EJB
    private CounterDataBean counterDataBean;

    /**
     * Асинхронный метод для получения архивных данных
     * @param objects список подписанных объектов
     */
    @Asynchronous
    public void initReadHistoricalFiles(List<SubscriptionObject> objects) {
        LOG.info("init read historical files for objects: " + objects.size() + " " + objects);

        for (SubscriptionObject object : objects) {
            List<DataModel> parameters = counterDataBean.getLinkedParameters(object.getId());

            LOG.info("Object: " + object.getObjectName() + " parameters count: " + parameters.size());

            //Подгрузка драйвера и загрузка данных по объекту
            if (parameters.size() != 0) {
                try {
                    Counter cl = (Counter) Class.forName(appSingletonBean.get(object.getServerName())).newInstance();
                    cl.loadData(parameters, object.getObjectName());
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
                    LOG.log(Level.WARNING, "error load historical data", ex);
                }

                parameters.removeIf(dataModel -> dataModel.getData().isEmpty());

                counterDataBean.putData(parameters);
            }
        }
    }

    /**
     * Асинхронный метод для получения данных по тревогам
     * @param objects список подписанных объектов
     */
    @Asynchronous
    public void initReadAlarmsFiles(List<SubscriptionObject> objects) {
        LOG.info("init read historical files for objects: " + objects.size() + " " + objects);

        for (SubscriptionObject object : objects) {
            List<DataModel> parameters = counterDataBean.getLinkedParameters(object.getId());

            LOG.info("Object: " + object.getObjectName() + " parameters count: " + parameters.size());

            //Подгрузка драйвера и загрузка данных по объекту
            if (parameters.size() != 0) {
                try {
                    Counter cl = (Counter) Class.forName(appSingletonBean.get(object.getServerName())).newInstance();
                    cl.loadAlarmData(parameters, object.getObjectName());
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
                    LOG.log(Level.WARNING, "error load historical data", ex);
                }

                parameters.removeIf(dataModel -> dataModel.getData().isEmpty());

                counterDataBean.putData(parameters);
            }
        }
    }

    /**
     * Метод возвращает слинкованные параметры объекта
     * @param opcObjectID id opc объекта
     * @return список слинкованных параметров
     */
    public ArrayList<DataModel> getLinkedParameters(String opcObjectID) {
        ArrayList<DataModel> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stmGetLinkedParameters = connect.prepareStatement(SELECT_LINKED_PARAMETERS);
             PreparedStatement stmGetStartDate = connect.prepareStatement(SELECT_START_DATE)) {
            LocalDateTime startDate;
            ResultSet resStartDate;

            stmGetLinkedParameters.setString(1, opcObjectID);

            ResultSet resLinked = stmGetLinkedParameters.executeQuery();
            while (resLinked.next()) {
                stmGetStartDate.setInt(1, resLinked.getInt(2));
                stmGetStartDate.setInt(2, resLinked.getInt(3));
                stmGetStartDate.setInt(3, resLinked.getInt(4));

                startDate = null;

                resStartDate = stmGetStartDate.executeQuery();
                while (resStartDate.next()) {
                    startDate = LocalDateTime.parse(resStartDate.getString(1), FORMAT);
                }

                result.add(new DataModel(resLinked.getString(1), resLinked.getInt(2), resLinked.getInt(3),
                        resLinked.getInt(4), startDate, resLinked.getString(5)));
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "error select linked parameters", ex);
        }
        return result;
    }

    /**
     * Асинхронный метод выгружает данные в базу по объекту
     * @param paramList данные для выгрузки в базу
     */
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void putData(List<DataModel> paramList) {
        try (OracleConnection connect = (OracleConnection) dsFil.getConnection();
             PreparedStatement stmAlter = connect.prepareStatement("alter session set nls_numeric_characters = '.,'");
             CallableStatement stm = connect.prepareCall(INSERT_DATA)) {
            stmAlter.execute();

            List<Object> dataList = new ArrayList<>();
            for (DataModel item: paramList) {
                for (ValueModel value: item.getData()) {
                    Date date = new java.sql.Date(value.getTime().plusHours(1)
                            .atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli());

                    Object[] row = {item.getObjectId(), item.getParamId(), item.getAggregateId(), value.getValue(), value.getQuality(), date, null};
                    Struct str = connect.createStruct("T_DZ_UTIL_INPUT_DATA_ROW", row);
                    dataList.add(str);
                }
            }

            if (dataList.size() > 0) {
                long timer = System.currentTimeMillis();
//                LOG.info("UploadObjectDataSBean.putData put: " + dataList.size() + " values " + paramList);

                Array array = connect.createOracleArray("T_DZ_UTIL_INPUT_DATA", dataList.toArray());

                stm.setArray(1, array);
                stm.execute();

                LOG.info("upload " + dataList.size() + " values; upload time " + (System.currentTimeMillis() - timer) + " ms");
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "error upload data " + paramList, ex);
        }
    }

    /**
     * Метод возвращает список подписанных объектов системы
     * @return списое объектов
     */
    public List<SubscriptionObject> getSubscribedObjects() {
        List<SubscriptionObject> objects = new ArrayList<>();

        try (Connection connect = ds.getConnection();
             PreparedStatement stmGetObjects = connect.prepareStatement(SELECT_SUBSCRIBED_OBJECTS)) {
            for (String server: appSingletonBean.getServers()) {
                stmGetObjects.setString(1, "%<Server>" + server + "</Server>");

                ResultSet res = stmGetObjects.executeQuery();
                while (res.next()) {
                    objects.add(new SubscriptionObject(res.getString(1), res.getString(2), server));
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "error load subscription objects", ex);
        }

        return objects;
    }
}
