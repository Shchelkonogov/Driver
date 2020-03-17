package ru.tecon.counter.VIST;

public enum VISTConfig {

    PTI("Количество теплоты нарастающим итогом"),
    PTD("Количество теплоты за время"),
    TIME_0("Нарастающее время в состоянии 0"),
    TIME_1("Нарастающее время в состоянии 1"),
    TIME_2("Нарастающее время в состоянии 2"),
    TIME_3("Нарастающее время в состоянии 3"),
    TIME_4("Нарастающее время в состоянии 4"),
    T1("Температура в подающем трубопроводе теплосети"),
    T2("Температура в обратном трубопроводе теплосети"),
    TP("Температура в подпиточном трубопроводе теплосети"),
    P1("Давление в подающем трубопроводе теплосети"),
    P2("Давление в обратном трубопроводе теплосети"),
    PT("Давление в подпиточном трубопроводе теплосети"),
    TIME_TS("Время ТС"),
    TIME_USPD("Время УСПД"),
    V1I("Объемный расход в подающем трубопроводе нарастающим итогом"),
    V2I("Объемный расход в обратном трубопроводе нарастающим итогом"),
    VPI("Объемный расход в подпиточном трубопроводе нарастающим итогом"),
    V1D("Объемный расход в подающем трубопроводе за время"),
    V2D("Объемный расход в обратном трубопроводе за время"),
    VPD("Объемный расход в подпиточном трубопроводе за время"),
    G1I("Массовый расход в подающем трубопроводе нарастающим итогом"),
    G2I("Массовый расход в обратном трубопроводе нарастающим итогом"),
    GPI("Массовый расход в подпиточном трубопроводе нарастающим итогом"),
    G1D("Массовый расход в подающем трубопроводе за время"),
    G2D("Массовый расход в обратном трубопроводе за время"),
    GPD("Массовый расход в подпиточном трубопроводе за время");

    private String property;

    VISTConfig(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
