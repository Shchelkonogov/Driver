package ru.tecon.sessionBean;

import oracle.jdbc.OracleConnection;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;

import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

@Stateless
public class Test {

    @Resource(mappedName = "jdbc/OracleDataSourceFil2")
    private DataSource dsFil2;

    private static final String SQL_INSERT_DATA = "{call dz_util1.input_data(?)}";

    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Future<Void> putData(List<DataModel> paramList) {
        try (OracleConnection connect = dsFil2.getConnection().unwrap(oracle.jdbc.OracleConnection.class);
                PreparedStatement stmAlter = connect.prepareStatement("alter session set nls_numeric_characters = '.,'");
             CallableStatement stm = connect.prepareCall(SQL_INSERT_DATA)) {
            stmAlter.execute();

            List<Object> dataList = new ArrayList<>();
            for (DataModel item: paramList) {
                for (ValueModel value: item.getData()) {
                    Date date = new java.sql.Date(value.getTime()
                            .atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli());

                    Object[] row = {item.getObjectId(), item.getParamId(), item.getAggrId(), value.getValue(), 192, date, null};
                    Struct str = connect.createStruct("T_DZ_UTIL_INPUT_DATA_ROW", row);
                    dataList.add(str);
                }
            }

            System.out.println(dataList.size());

            Array array = connect.createARRAY("T_DZ_UTIL_INPUT_DATA", dataList.toArray());

            stm.setArray(1, array);
            stm.execute();

            System.out.println("ok" + dataList.size() + " " + System.currentTimeMillis());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
