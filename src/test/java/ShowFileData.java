public class ShowFileData {

    public static void main(String[] args) {
        checkMCT();
        checkMCTSLAVE();
        checkSA();
        checkTEROS();
        checkVIST();
    }

    private static void checkMCT() {
        ru.tecon.counter.counterImpl.MCT20.Driver driverMCT = new ru.tecon.counter.counterImpl.MCT20.Driver();
        System.out.println("MCT20");
        System.out.println("Version 4");
        driverMCT.printData("//172.16.4.47/c$/inetpub/ftproot/00/0087/0087a20201002-05");
    }

    private static void checkMCTSLAVE() {
        ru.tecon.counter.counterImpl.MCT20_SLAVE.Driver driverMCT20SLAVE = new ru.tecon.counter.counterImpl.MCT20_SLAVE.Driver();
        System.out.println("MCT SLAVE");
        driverMCT20SLAVE.printData("//172.16.4.47/c$/inetpub/ftproot/00/0087/0087b20201005-04");
    }

    private static void checkSA() {
        ru.tecon.counter.counterImpl.SA94.Driver driverSA = new ru.tecon.counter.counterImpl.SA94.Driver();
        System.out.println("SA94");
        System.out.println("Normal");
        driverSA.printData("//172.16.4.47/c$/inetpub/ftproot/02/0258/0258s20200305-13");

        driverSA = new ru.tecon.counter.counterImpl.SA94.Driver();
        System.out.println("SA94");
        System.out.println("Extended");
        driverSA.printData("//172.16.4.47/c$/inetpub/ftproot/01/0125/0125e20201106-07");
    }

    private static void checkVIST() {
        ru.tecon.counter.counterImpl.VIST.Driver driverVIST = new ru.tecon.counter.counterImpl.VIST.Driver();
        System.out.println("VIST");
        driverVIST.printData("//172.16.4.47/c$/inetpub/ftproot/01/0140/0140v20201106-03");
    }

    private static void checkTEROS() {
        ru.tecon.counter.counterImpl.TEROS.Driver driverTEROS = new ru.tecon.counter.counterImpl.TEROS.Driver();
        System.out.println("TEROS");
        driverTEROS.printData("//172.16.4.47/c$/inetpub/ftproot/00/0091/0091t20201106-02");
    }
}
