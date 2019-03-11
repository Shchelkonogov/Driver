package ru.tecon.servlet;

import ru.tecon.counter.Counter;
import ru.tecon.sessionBean.LoadOPCSBean;

import javax.ejb.EJB;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Сервлет для выгрузки всех номеров счетчиков МСТ-20 с ftp
 */
@WebServlet(urlPatterns = "/scan")
public class ScanAddressSpace extends HttpServlet {

    @EJB
    private LoadOPCSBean bean;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String folder = null;

        try {
            Class<?> counterClass = Class.forName("ru.tecon.counter.MCT20.driver.Driver");
            Counter counter = (Counter) counterClass.newInstance();
            folder = counter.getURL();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

        List<String> objects = bean.scanFolder(folder);
        bean.insertOPCObjects(objects);

        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html");

        ServletOutputStream os = resp.getOutputStream();
        os.println("<div style=\"text-align: center\">");
        os.println("Обновление выполнено");
        os.println("</div>");
    }
}
