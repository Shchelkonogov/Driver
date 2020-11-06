package ru.tecon.counter.counterImpl.IASDTU;

import ru.tecon.counter.Counter;
import ru.tecon.counter.counterImpl.IASDTU.sessionBean.TransferBLocal;
import ru.tecon.counter.model.DataModel;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.List;
import java.util.logging.Logger;

public class Driver extends Counter {

    private TransferBLocal bean;

    public Driver() {
        Logger log = Logger.getLogger(Driver.class.getName());
        log.info("Driver инициализация драйвера и загрузка бина");
        try {
            Context ctx = new InitialContext();
            bean = (TransferBLocal) ctx.lookup("java:comp/transfer");
        } catch (NamingException e) {
            log.warning("Ошибка загрузки бина");
        }
    }

    @Override
    public List<String> getObjects() {
        return bean.getObjects();
    }

    @Override
    public List<String> getConfig(String object) {
        return bean.getConfig(object);
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        bean.loadData(params, objectName);
    }
}
