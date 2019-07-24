package ru.tecon.sessionBean.counterData;

import ru.tecon.counter.Counter;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;
import ru.tecon.model.SubscriptionObject;
import ru.tecon.sessionBean.AppConfigSBean;

import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Stateless bean с асинхронным методом выгрузки данных по объекту
 */
@Stateless
public class LoadObjectDataSBean {

    private static final Logger LOG = Logger.getLogger(LoadObjectDataSBean.class.getName());

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH");

    private static final String SQL_GET_LINKED_PARAMETERS = "select c.display_name, b.aspid_object_id, b.aspid_param_id, b.aspid_agr_id " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element where opc_object_id = ?) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id)";
    private static final String SQL_GET_START_DATE = "select to_char(time_stamp, 'dd.mm.yyyy hh24') " +
            "from dz_input_start where obj_id = ? and par_id = ? and stat_aggr = ?";
    private static final String SQL_ADD_VALUE = "select measure_unit_transformer from tsa_linked_element " +
            "where aspid_object_id = ? and aspid_agr_id = ? and aspid_param_id = ?";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @EJB
    private UploadObjectDataSBean bean;

    @EJB
    private AppConfigSBean appBean;

    /**
     * Асинхронный метод, который выгружает параметры по объекту,
     * подгружает драйвер, грузит данные по объекту и передает
     * управление асинхронному бину для выгрузки данных в базу
     * @param objects список объектов для загрузки
     * @return null
     */
    @Asynchronous
    public Future<Void> loadObjectParams(List<SubscriptionObject> objects) {
        LOG.info("LoadObjectDataSBean.loadObjectParams list: " + objects.size() + " " + objects);
        LocalDateTime startDate;

        List<DataModel> globalParamList = new ArrayList<>();

        for (SubscriptionObject object: objects) {
            List<DataModel> paramList = new ArrayList<>();

            try (Connection connect = ds.getConnection();
                 PreparedStatement stmGetLinkedParameters = connect.prepareStatement(SQL_GET_LINKED_PARAMETERS);
                 PreparedStatement stmGetStartDate = connect.prepareStatement(SQL_GET_START_DATE)) {
                ResultSet resStartDate;

                stmGetLinkedParameters.setString(1, object.getId());

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

                    paramList.add(new DataModel(resLinked.getString(1), resLinked.getInt(2), resLinked.getInt(3),
                            resLinked.getInt(4), startDate));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            LOG.info("LoadObjectDataSBean.loadObjectParams object: " + object.getObjectName() + " parameters count: " + paramList.size());

            //Подгрузка драйвера и загрузка данных по объекту
            if (paramList.size() != 0) {
                try {
                    Counter cl = (Counter) Class.forName(appBean.get(object.getServerName())).newInstance();
                    cl.loadData(paramList, object.getObjectName());
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }

            globalParamList.addAll(paramList);
        }

        //Некоторые параметры требуется умножить на значение из базы
        //Получение этих значений и инкрементация данных
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_ADD_VALUE)) {
            for (DataModel item: globalParamList) {
                stm.setInt(1, item.getObjectId());
                stm.setInt(2, item.getAggrId());
                stm.setInt(3, item.getParamId());

                try {
                    ResultSet res = stm.executeQuery();
                    res.next();

                    if (res.getString(1) != null) {
                        BigDecimal increment = new BigDecimal(res.getString(1).substring(2));
                        for (ValueModel model: item.getData()) {
                            model.setValue(new BigDecimal(model.getValue()).multiply(increment).toString());
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

//        LOG.info("LoadObjectDataSBean.loadObjectParams parameters with data: " + globalParamList);

        bean.putData(globalParamList);

        return null;
    }
}
