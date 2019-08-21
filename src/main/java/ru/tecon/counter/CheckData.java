package ru.tecon.counter;

import ru.tecon.counter.MCT20.Driver;

public class CheckData {

    public static void main(String[] args) {
        Driver driverMCT = new Driver();
        System.out.println("MCT20");
        System.out.println("Version 3");
        driverMCT.printData("//172.16.4.47/c$/inetpub/ftproot/00/0069/ans-20190327-05");
        driverMCT = new Driver();
        System.out.println("Version 4");
        driverMCT.printData("//172.16.4.47/c$/inetpub/ftproot/00/0006/0006a20190621-06");

        ru.tecon.counter.SA94.Driver driverSA = new ru.tecon.counter.SA94.Driver();
        System.out.println("SA94");
        System.out.println("Normal");
        driverSA.printData("//172.16.4.47/c$/inetpub/ftproot/01/0190/0190s20190722-12");
        driverSA = new ru.tecon.counter.SA94.Driver();
        System.out.println("Extended");
        driverSA.printData("//172.16.4.47/c$/inetpub/ftproot/02/0231/0231e20190821-08");
    }
}
