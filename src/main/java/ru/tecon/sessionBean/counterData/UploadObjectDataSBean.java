package ru.tecon.sessionBean.counterData;

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
import java.util.logging.Logger;

/**
 * Stateless bean с асинхронным методом загрузки данных по объекту в базу
 */
@Stateless
public class UploadObjectDataSBean {

    private static final Logger LOG = Logger.getLogger(UploadObjectDataSBean.class.getName());

    @Resource(name = "jdbc/DataSourceFil")
    private DataSource dsFil2;

    private static final String SQL_INSERT_DATA = "{call dz_util1.input_data(?)}";

    /**
     * Асинхронный метод выгружает данные в базу по объекту
     * @param paramList данные для выгрузки в базу
     * @return null
     */
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
                    Date date = new java.sql.Date(value.getTime().plusHours(1)
                            .atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli());

                    Object[] row = {item.getObjectId(), item.getParamId(), item.getAggrId(), value.getValue(), 192, date, null};
                    Struct str = connect.createStruct("T_DZ_UTIL_INPUT_DATA_ROW", row);
                    dataList.add(str);
                }
            }

            if (dataList.size() > 0) {
                long timer = System.currentTimeMillis();
//                LOG.info("UploadObjectDataSBean.putData put: " + dataList.size() + " values " + dataList);

                Array array = connect.createOracleArray("T_DZ_UTIL_INPUT_DATA", dataList.toArray());

                stm.setArray(1, array);
                stm.execute();

                LOG.info("UploadObjectDataSBean.putData done put: " + dataList.size() + " values " + (System.currentTimeMillis() - timer));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            LOG.warning("UploadObjectDataSBean.putData error upload: " + paramList);
        }
        return null;
    }
}
