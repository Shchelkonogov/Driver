package ru.tecon.servlet;

import ru.tecon.counter.Counter;
import ru.tecon.sessionBean.AppConfigSBean;
import ru.tecon.sessionBean.LoadOPCSBean;

import javax.ejb.EJB;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Сервлет для выгрузки всех opc объектов в базу
 * Обязательный параметр ?counter=;
 */
@WebServlet(urlPatterns = "/scan")
public class ScanAddressSpace extends HttpServlet {

    @EJB
    private LoadOPCSBean bean;

    @EJB
    private AppConfigSBean appBean;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String counterName = req.getParameter("counter");

        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html");
        ServletOutputStream os = resp.getOutputStream();

        if ((counterName != null) && appBean.containsKey(counterName)) {
            try {
                Counter counter = (Counter) Class.forName(appBean.get(counterName)).newInstance();
                bean.insertOPCObjects(counter.getObjects(), counterName);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }

            os.println("<div style=\"text-align: center\">");
            os.println("Обновление выполнено");
            os.println("</div>");
        } else {
            os.println("<div style=\"text-align: center\">");
            os.println("Неизвестный счетчик");
            os.println("</div>");
        }
    }
}
