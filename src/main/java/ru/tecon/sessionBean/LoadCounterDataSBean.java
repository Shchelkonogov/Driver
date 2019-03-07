package ru.tecon.sessionBean;

import ru.tecon.counter.Counter;
import ru.tecon.counter.model.DataModel;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Startup
@Singleton
public class LoadCounterDataSBean {

    private boolean test = true;
    private LocalDateTime startDate = LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS);

    private static final String SQL_GET_OBJECTS = "select a.id, a.display_name from tsa_opc_object a " +
            "where opc_path like '%<Server>MCT-20</Server>' " +
            "and exists(select 1 from tsa_linked_object where opc_object_id = a.id and subscribed = 1)";
    private static final String SQL_GET_LINKED_PARAMETERS = "select c.display_name, b.aspid_object_id, b.aspid_param_id, b.aspid_agr_id " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element where opc_object_id = ?) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id)";
    private static final String SQL_GET_START_DATE = "select to_char(time_stamp, 'dd.mm.yyyy hh24') " +
            "from dz_input_start where obj_id = ? and par_id = ? and stat_aggr = ?";

    @Resource(mappedName = "jdbc/OracleDataSource")
    private DataSource ds;

    @EJB
    private Test bean;

    @Schedule(second = "*/10", minute="*", hour="*", persistent = false)
    private void timer() {
//        TODO убрать в боевом варианте
        if (test) {
            test = false;
        } else {
            return;
        }

        try (Connection connect = ds.getConnection();
                PreparedStatement stmGetObjects = connect.prepareStatement(SQL_GET_OBJECTS)) {
            ResultSet res = stmGetObjects.executeQuery();
            while (res.next()) {
                loadObjectParams(res.getString(1), res.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

//    TODO Сделать опрос по улитке (если данных нету на данный час то опрашивать еще в надежде что данные придут)

    private void loadObjectParams(String objectId, String objectName) {
        List<DataModel> paramList = new ArrayList<>();

        try (Connection connect = ds.getConnection();
                PreparedStatement stmGetLinkedParameters = connect.prepareStatement(SQL_GET_LINKED_PARAMETERS);
                PreparedStatement stmGetStartDate = connect.prepareStatement(SQL_GET_START_DATE)) {
            ResultSet resStartDate;

            stmGetLinkedParameters.setString(1, objectId);

            ResultSet resLinked = stmGetLinkedParameters.executeQuery();
            while (resLinked.next()) {
                stmGetStartDate.setInt(1, resLinked.getInt(2));
                stmGetStartDate.setInt(2, resLinked.getInt(3));
                stmGetStartDate.setInt(3, resLinked.getInt(4));

                resStartDate = stmGetStartDate.executeQuery();
                while (resStartDate.next()) {
                    startDate = LocalDateTime.parse(resLinked.getString(1), DateTimeFormatter.ofPattern("dd.MM.yyyy HH"));
                }

                paramList.add(new DataModel(resLinked.getString(1), resLinked.getInt(2), resLinked.getInt(3),
                        resLinked.getInt(4), startDate));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Parameters: " + paramList);

        try {
            Counter cl = (Counter) Class.forName("ru.tecon.counter.MCT20.driver.Driver").newInstance();

            cl.loadData(paramList, objectName);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }

        System.out.println(paramList);
        System.out.println(paramList.size());

        System.out.println(System.currentTimeMillis());
        bean.putData(paramList.subList(0, (paramList.size() / 2) - 1));
        bean.putData(paramList.subList((paramList.size() / 2) - 1, paramList.size()));
        System.out.println(System.currentTimeMillis());
    }
}
