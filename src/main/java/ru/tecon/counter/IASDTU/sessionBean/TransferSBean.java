package ru.tecon.counter.IASDTU.sessionBean;

import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Stateless(name = "transfer")
public class TransferSBean implements TransferBLocal {

    private static final Logger LOG = Logger.getLogger(TransferSBean.class.getName());

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH");
    private static final DateTimeFormatter FORMAT_MS_SQL_SERVER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SQL = "select distinct(station) from ParametersLibraryDisp";
    private static final String SQL_CONFIG = "select name from ParametersLibraryDisp where station = ?";
    private static final String SQL_DATA = "select VACValue, convert(varchar, VACtimestamp, 120) from bas_ValuesAvgCalc " +
            "where linkID = (select LinkID from ParametersLibraryDisp " +
            "where station = ? and name = ?) " +
            "and VACtimestamp > convert(DATETIME, ?) " +
            "order by VACtimestamp";

    private static final String PRE_OBJECT_NAME = "ИАСДТУ_";

    @Resource(name = "jdbc/iasdtu")
    private DataSource ds;

    @Override
    public List<String> getObjects() {
        LOG.info("TransferSBean.getObjects: start");
        List<String> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
                PreparedStatement stm = connect.prepareStatement(SQL)) {
            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(PRE_OBJECT_NAME + res.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        LOG.info("TransferSBean.getObjects: end");
        return result;
    }

    @Override
    public List<String> getConfig(String object) {
        LOG.info("TransferSBean.getConfig: start " + object);
        List<String> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
                PreparedStatement stm = connect.prepareStatement(SQL_CONFIG)) {
            stm.setString(1, object.replace(PRE_OBJECT_NAME, ""));

            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(res.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        LOG.info("TransferSBean.getConfig: end " + object);
        return result;
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        LOG.info("TransferSBean.loadData: start " + objectName);
        try (Connection connect = ds.getConnection();
                PreparedStatement stm = connect.prepareStatement(SQL_DATA)) {
            stm.setFetchSize(10000);

            for (DataModel item: params) {
                if (item.getStartTime() == null) {
                    item.setStartTime(LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS));
                }

                stm.setString(1, objectName.replace(PRE_OBJECT_NAME, ""));
                stm.setString(2, item.getParamName());
                stm.setString(3, item.getStartTime().plusHours(3).format(FORMAT) + ":00");

                ResultSet res = stm.executeQuery();
                while (res.next()) {
                    item.addData(new ValueModel(res.getString(1),
                            LocalDateTime.parse(res.getString(2), FORMAT_MS_SQL_SERVER).minusHours(4)));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        LOG.info("TransferSBean.loadData: end " + objectName);
    }
}
