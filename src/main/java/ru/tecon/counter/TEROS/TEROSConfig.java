package ru.tecon.counter.TEROS;

public enum TEROSConfig {

    PTI("Количество теплоты нарастающим итогом"),//q
    PTD("Количество теплоты за время"),//--
    TIME_0("Нарастающее время в состоянии 0"),//trab
    TIME_1("Нарастающее время в состоянии 1"),//tdt_min
    TIME_2("Нарастающее время в состоянии 2"),//Tgmin
    TIME_3("Нарастающее время в состоянии 3"),//Tgmax
    TIME_4("Нарастающее время в состоянии 4"),//terr
    T1("Температура в подающем трубопроводе теплосети"), //vt1
    T2("Температура в обратном трубопроводе теплосети"), //vt2
    TP("Температура в подпиточном трубопроводе теплосети"), //vt3
    P1("Давление в подающем трубопроводе теплосети"), //vp1
    P2("Давление в обратном трубопроводе теплосети"), //vp2
    PT("Давление в подпиточном трубопроводе теплосети"), //vp3
    TIME_TS("Время ТС"), //терос
    TIME_USPD("Время УСПД"), //mct20
    V1I("Объемный расход в подающем трубопроводе нарастающим итогом"),//v1
    V2I("Объемный расход в обратном трубопроводе нарастающим итогом"),//v2
    VPI("Объемный расход в подпиточном трубопроводе нарастающим итогом"),//v3
    V1D("Объемный расход в подающем трубопроводе за время"),//--
    V2D("Объемный расход в обратном трубопроводе за время"),//--
    VPD("Объемный расход в подпиточном трубопроводе за время"),//--
    G1I("Массовый расход в подающем трубопроводе нарастающим итогом"),//m1
    G2I("Массовый расход в обратном трубопроводе нарастающим итогом"),//m2
    GPI("Массовый расход в подпиточном трубопроводе нарастающим итогом"),//m3
    G1D("Массовый расход в подающем трубопроводе за время"),//--
    G2D("Массовый расход в обратном трубопроводе за время"),//--
    GPD("Массовый расход в подпиточном трубопроводе за время");//--

    private String property;

    TEROSConfig(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
