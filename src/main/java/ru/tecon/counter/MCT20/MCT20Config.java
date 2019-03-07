package ru.tecon.counter.MCT20;

public enum MCT20Config {

    TOTAL_TIME("Общее время работы"),

    WATER_VOLUME0("Объем воды за время канал 0"),
    WATER_VOLUME1("Объем воды за время канал 1"),
    WATER_VOLUME2("Объем воды за время канал 2"),
    WATER_VOLUME3("Объем воды за время канал 3"),
    WATER_VOLUME4("Объем воды за время канал 4"),
    WATER_VOLUME5("Объем воды за время канал 5"),
    WATER_VOLUME6("Объем воды за время канал 6"),
    WATER_VOLUME7("Объем воды за время канал 7"),

    WATER_WEIGHT0("Масса воды за время канал 0"),
    WATER_WEIGHT1("Масса воды за время канал 1"),
    WATER_WEIGHT2("Масса воды за время канал 2"),
    WATER_WEIGHT3("Масса воды за время канал 3"),
    WATER_WEIGHT4("Масса воды за время канал 4"),
    WATER_WEIGHT5("Масса воды за время канал 5"),
    WATER_WEIGHT6("Масса воды за время канал 6"),
    WATER_WEIGHT7("Масса воды за время канал 7"),

    WATER_TEMPER0("Температура за время канал 0"),
    WATER_TEMPER1("Температура за время канал 1"),
    WATER_TEMPER2("Температура за время канал 2"),
    WATER_TEMPER3("Температура за время канал 3"),
    WATER_TEMPER4("Температура за время канал 4"),
    WATER_TEMPER5("Температура за время канал 5"),
    WATER_TEMPER6("Температура за время канал 6"),
    WATER_TEMPER7("Температура за время канал 7"),

    WATER_PRESSURE0("Давление используемое в рассчетах канал 0"),
    WATER_PRESSURE1("Давление используемое в рассчетах канал 1"),
    WATER_PRESSURE2("Давление используемое в рассчетах канал 2"),
    WATER_PRESSURE3("Давление используемое в рассчетах канал 3"),
    WATER_PRESSURE4("Давление используемое в рассчетах канал 4"),
    WATER_PRESSURE5("Давление используемое в рассчетах канал 5"),
    WATER_PRESSURE6("Давление используемое в рассчетах канал 6"),
    WATER_PRESSURE7("Давление используемое в рассчетах канал 7"),

    WATER_HEAT_AMOUNT0("Количество тепла за время канал 0"),
    WATER_HEAT_AMOUNT1("Количество тепла за время канал 1"),
    WATER_HEAT_AMOUNT2("Количество тепла за время канал 2"),
    WATER_HEAT_AMOUNT3("Количество тепла за время канал 3"),
    WATER_HEAT_AMOUNT4("Количество тепла за время канал 4"),
    WATER_HEAT_AMOUNT5("Количество тепла за время канал 5"),
    WATER_HEAT_AMOUNT6("Количество тепла за время канал 6"),
    WATER_HEAT_AMOUNT7("Количество тепла за время канал 7"),

    WATER_ACCUMULATED0("Объем воды накопленный канал 0"),
    WATER_ACCUMULATED1("Объем воды накопленный канал 1"),
    WATER_ACCUMULATED2("Объем воды накопленный канал 2"),
    WATER_ACCUMULATED3("Объем воды накопленный канал 3"),
    WATER_ACCUMULATED4("Объем воды накопленный канал 4"),
    WATER_ACCUMULATED5("Объем воды накопленный канал 5"),
    WATER_ACCUMULATED6("Объем воды накопленный канал 6"),
    WATER_ACCUMULATED7("Объем воды накопленный канал 7"),

    WATER_MASS_ACCUMULATED0("Масса воды накопленная канала 0"),
    WATER_MASS_ACCUMULATED1("Масса воды накопленная канала 1"),
    WATER_MASS_ACCUMULATED2("Масса воды накопленная канала 2"),
    WATER_MASS_ACCUMULATED3("Масса воды накопленная канала 3"),
    WATER_MASS_ACCUMULATED4("Масса воды накопленная канала 4"),
    WATER_MASS_ACCUMULATED5("Масса воды накопленная канала 5"),
    WATER_MASS_ACCUMULATED6("Масса воды накопленная канала 6"),
    WATER_MASS_ACCUMULATED7("Масса воды накопленная канала 7"),

    WATER_HEAT_ACCUMULATED0("Количество тепла накопленное канал 0"),
    WATER_HEAT_ACCUMULATED1("Количество тепла накопленное канал 1"),
    WATER_HEAT_ACCUMULATED2("Количество тепла накопленное канал 2"),
    WATER_HEAT_ACCUMULATED3("Количество тепла накопленное канал 3"),
    WATER_HEAT_ACCUMULATED4("Количество тепла накопленное канал 4"),
    WATER_HEAT_ACCUMULATED5("Количество тепла накопленное канал 5"),
    WATER_HEAT_ACCUMULATED6("Количество тепла накопленное канал 6"),
    WATER_HEAT_ACCUMULATED7("Количество тепла накопленное канал 7"),

    HEAT_AMOUNT_GVS("Количество теплоты в системе ГВС"),
    HEAT_AMOUNT_CO("Количество теплоты в системе отопления"),
    HEAT_AMOUNT_VENT("Количество теплоты в системе вентиляции"),

    HEAT_AMOUNT_ACCUMULATED_GVS("Накопленное количество теплоты в системе ГВС"),
    HEAT_AMOUNT_ACCUMULATED_CO("Накопленное количество теплоты в системе отопления"),
    HEAT_AMOUNT_ACCUMULATED_VENT("Накопленное количество теплоты в системе вентиляции");

    private String property;

    MCT20Config(String property) { this.property = property; }

    public String getProperty() {
        return property;
    }
}
