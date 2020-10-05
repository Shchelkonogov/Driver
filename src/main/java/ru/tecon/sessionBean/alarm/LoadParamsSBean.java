package ru.tecon.sessionBean.alarm;

import ru.tecon.alarm.Parse;
import ru.tecon.counter.model.DataModel;
import ru.tecon.model.SubscriptionObject;
import ru.tecon.sessionBean.counterData.UploadObjectDataSBean;

import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

@Stateless
public class LoadParamsSBean {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH");

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource(name = "url")
    private String alarmUrl;

    @EJB
    private UploadObjectDataSBean bean;

    private static final String SQL_GET_LINKED_PARAMETERS = "select c.display_name, b.aspid_object_id, b.aspid_param_id, " +
            "b.aspid_agr_id, b.measure_unit_transformer " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where c.display_name = 'Состояние электрического ввода' " +
            "and b.opc_element_id in (select id from tsa_opc_element where opc_object_id = ?) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
            "and b.aspid_agr_id is not null";
    private static final String SQL_GET_START_DATE = "select to_char(time_stamp, 'dd.mm.yyyy hh24') " +
            "from dz_input_start where obj_id = ? and par_id = ? and stat_aggr = ?";

    @Asynchronous
    public Future<Void> loadObjectParams(List<SubscriptionObject> objects) {
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

            Parse.parse(paramList, alarmUrl + "/alarms", object.getObjectName());

            globalParamList.addAll(paramList);
        }

        bean.putData(globalParamList);

        return null;
    }
}
