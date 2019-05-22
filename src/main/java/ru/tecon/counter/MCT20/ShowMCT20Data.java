package ru.tecon.counter.MCT20;

import ru.tecon.counter.MCT20.driver.Driver;

public class ShowMCT20Data {

    public static void main(String[] args) {
        Driver driver = new Driver();
        driver.printData("//172.16.4.47/c$/inetpub/ftproot/00/0069/ans-20190327-05");
//        driver.printData("//172.16.4.47/c$/inetpub/ftproot/00/0006/0006a20190521-06");
    }
}
