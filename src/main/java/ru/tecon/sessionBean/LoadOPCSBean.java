package ru.tecon.sessionBean;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Stateless
public class LoadOPCSBean {

    private final static Logger LOG = Logger.getLogger(LoadOPCSBean.class.getName());

    private static final String SQL_INSERT_OPC_OBJECT = "insert into tsa_opc_object values((select get_guid_base64 from dual), ?, ?, 1)";

    @Resource(mappedName = "jdbc/OracleDataSource")
    private DataSource ds;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void insertOPCObjects(List<String> objects) {
        try (Connection connect = ds.getConnection();
                PreparedStatement stmInsertObject = connect.prepareStatement(SQL_INSERT_OPC_OBJECT)) {
            for (String object: objects) {
                stmInsertObject.setString(1, object);
                stmInsertObject.setString(2, "<OpcKind>Hda</OpcKind><ItemName>" + object + "</ItemName><Server>MCT-20</Server>");
                try {
                    LOG.info("LoadOPCSBean.insertOPCObjects Объект: " + object);
                    stmInsertObject.executeUpdate();
                    LOG.info("LoadOPCSBean.insertOPCObjects Успешная вставка");
                } catch(SQLException e) {
                    LOG.warning("LoadOPCSBean.insertOPCObjects Данная запись уже существует");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> scanFolder(String folder) {
        List<String> result = new ArrayList<>();
        search(result, folder, "\\d\\d");
        return result;
    }

    private void search(List<String> result, String folder, String regex) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(folder),
                entry -> entry.getFileName().toString().matches(regex))) {
            for (Path entry: stream) {
                if(Files.isDirectory(entry)) {
                    switch(entry.getFileName().toString().length()) {
                        case 2:
                            search(result, entry.toString(), entry.getFileName() + "\\d\\d");
                            break;
                        case 4:
                            result.add("МСТ-20-" + entry.getFileName());
                            break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
