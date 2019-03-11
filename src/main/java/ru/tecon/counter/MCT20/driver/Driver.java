package ru.tecon.counter.MCT20.driver;

import ru.tecon.counter.Counter;
import ru.tecon.counter.MCT20.MCT20Config;
import ru.tecon.counter.MCT20.MCT20CounterParameter;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

public class Driver implements Counter {

    private static final Logger LOG = Logger.getLogger(Driver.class.getName());

    private static final String URL = "//172.16.4.47/c$/inetpub/ftproot";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HH");

    private long totalTime;
    private float[] waterVolume = new float[8];
    private float[] waterWeight = new float[8];
    private float[] waterTemper = new float[8];
    private float[] waterPressure = new float[8];
    private float[] waterHeatAmount = new float[8];
    private float[] waterAccumulated = new float[8];
    private float[] waterMassAccumulated = new float[8];
    private float[] waterHeatAccumulated = new float[8];
    private float heatAmountGVS;
    private float heatAmountCO;
    private float heatAmountVENT;
    private float heatAmountAccumulatedGVS;
    private float heatAmountAccumulatedCO;
    private float heatAmountAccumulatedVENT;

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        Collections.sort(params);

        String counterNumber = objectName.substring(objectName.length() - 4);
        String filePath = URL + "/" + counterNumber.substring(0, 2) + "/" + counterNumber;

        LocalDateTime date = params.get(0).getStartTime();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(3);

        Class<?> cl = this.getClass();
        Map<String, String> methodsMap = this.getMethodsMap();

        while (date.isBefore(now)) {
            String fileName = filePath + "/ans-" + date.format(DATE_FORMAT);

            LOG.info("Driver.loadData check file: " + fileName + " " + System.currentTimeMillis());

            if (Files.exists(Paths.get(fileName))) {
                readFile(fileName);

                for (DataModel model: params) {
                    if (!date.isBefore(model.getStartTime())) {
                        try {
                            String mName = methodsMap.get(model.getParamName());
                            if (Objects.nonNull(mName)) {
                                Object value = cl.getMethod(mName).invoke(this);
                                if (value instanceof Long) {
                                    model.addData(new ValueModel(Long.toString((Long) value), date));
                                } else {
                                    if (value instanceof Float) {
                                        model.addData(new ValueModel(Float.toString((Float) value), date));
                                    }
                                }
                            }
                        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            date = date.plusHours(1);
        }
    }

    private void readFile(String path) {
        LOG.info("Driver.readFile start read: " + path + " " + System.currentTimeMillis());
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(Paths.get(path)))) {
            boolean head = true;

            int  bufferSize = 385;
            byte buffer[] = new byte[385];

            int recordNumber = 1;

            while(inputStream.available() > 0) {
                if (head) {
                    //uint16_t lenA     uint8_t ver
                    if((readInt(inputStream, 2) != 6) || (readInt(inputStream, 1) != 3)) {
                        LOG.warning("Driver.readFile Неверный размер заголовка или версия протокола");
                        break;
                    }
                    //uint16_t reqNumA
                    LOG.info("Driver.readFile Номер запроса: " + readInt(inputStream, 2));
                    //uint8_t reqRes
                    if(readInt(inputStream, 1) != 0) {
                        LOG.warning("Driver.readFile Результат обработки запроса выдал ошибку");
                        break;
                    }

                    //uint16_t lenD
                    LOG.info("Driver.readFile Размер секции данных: " + readInt(inputStream, 2));
                    //uint8_t shema
                    LOG.info("Driver.readFile Номер схемы подключения: " + readInt(inputStream, 1));
                    //uint8_t chCnt
                    LOG.info("Driver.readFile Количество каналов: " + readInt(inputStream, 1));
                    //uint16_t cntAz
                    if (readInt(inputStream, 2) != 11) {
                        LOG.warning("Driver.readFile Неверное количество архивных записей");
                        break;
                    }
                    //uint16_t crcA
                    LOG.info("Driver.readFile Контрольная сумма: " + readInt(inputStream, 2));
                    head = false;
                }

                if (inputStream.available() < 384) {
                    bufferSize = inputStream.available();
                }
                int sizeAvailable = inputStream.read(buffer, 0, bufferSize);
//                LOG.info("Driver.readFile Блок данных размером: " + sizeAvailable);

//                System.out.println("----------------АРХИВНАЯ ЗАПИСЬ----------------");
                if((buffer[0] & 0xff) != 3) {
                    LOG.warning("Driver.readFile Неверный формат архивной записи");
                    break;
                }

                if ((buffer[bufferSize - 1] & 0xff) != 10) {
                    LOG.warning("Driver.readFile Ошибочное окончание записи");
                    break;
                }

                if (recordNumber != 11) {
                    recordNumber++;
                    continue;
                }

//                System.out.println("ХЗ ЧТО ЭТО: " + (buffer[1] & 0xff));
//                System.out.println("ХЗ ЧТО ЭТО: " + (buffer[2] & 0xff));
//                System.out.println("ХЗ ЧТО ЭТО: " + (buffer[3] & 0xff));
//
//                System.out.println("Год: 20" + (buffer[4] & 0xff));
//                System.out.println("Месяц: " + (buffer[5] & 0xff));
//                System.out.println("День: " + (buffer[6] & 0xff));
//                System.out.println("Час: " + (buffer[7] & 0xff));

                totalTime = (((buffer[11] & 0xff) << 24) | ((buffer[10] & 0xff) << 16) | ((buffer[9] & 0xff) << 8) | (buffer[8] & 0xff));
//                System.out.println("Время накопления данных, сек: " + (((buffer[15] & 0xff) << 24) | ((buffer[14] & 0xff) << 16) | ((buffer[13] & 0xff) << 8) | (buffer[12] & 0xff)));

                int index = 16;
                for(int i = 0; i < 8; i++) {
                    waterVolume[i] = readFloat(buffer, index, 0);

                    waterWeight[i] = readFloat(buffer, index, 32);

                    waterTemper[i] = readFloat(buffer, index, 64);

                    waterPressure[i] = readFloat(buffer, index, 96);

                    waterHeatAmount[i] = readFloat(buffer, index, 128);

//                    System.out.println("Диагностика измерения объема воды за время канал " + i + ":" +
//                            (((buffer[index + 160 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 160 - (2 * i)] & 0xff)));
//                    System.out.println("Диагностика измерения массы воды за время канал " + i + ":" +
//                            (((buffer[index + 176 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 176 - (2 * i)] & 0xff)));
//                    System.out.println("Диагностика измерения температуры за время канал " + i + ":" +
//                            (((buffer[index + 192 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 192 - (2 * i)] & 0xff)));
//                    System.out.println("Диагностика давления за время канал " + i + ":" +
//                            (((buffer[index + 208 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 208 - (2 * i)] & 0xff)));
//                    System.out.println("Диагностика расчета тепла за время канал " + i + ":" +
//                            (((buffer[index + 224 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 224 - (2 * i)] & 0xff)));

                    waterAccumulated[i] = readFloat(buffer, index, 240);

                    waterMassAccumulated[i] = readFloat(buffer, index, 272);

                    waterHeatAccumulated[i] = readFloat(buffer, index, 304);

                    index += 4;
                }
                index += 304;

//                System.out.println("Диагностика расчетов по схеме 1: " +
//                        (((buffer[index + 3] & 0xff) << 24) |
//                        ((buffer[index + 2] & 0xff) << 16) |
//                        ((buffer[index + 1] & 0xff) << 8) |
//                        (buffer[index] & 0xff)));
                index += 4;

                heatAmountGVS = readFloat(buffer, index, 0);
                index += 4;

                heatAmountCO = readFloat(buffer, index, 0);
                index += 4;

                heatAmountVENT = readFloat(buffer, index, 0);
                index += 4;

                heatAmountAccumulatedGVS = readFloat(buffer, index, 0);
                index += 4;

                heatAmountAccumulatedCO = readFloat(buffer, index, 0);
                index += 4;

                heatAmountAccumulatedVENT = readFloat(buffer, index, 0);
                index += 4;

//                System.out.println("Диагностика прибора: " + (((buffer[index + 1] & 0xff) << 8) | (buffer[index] & 0xff)));
                index += 2;

//                System.out.println("Контрольная сумма записи: " + (((buffer[index + 1] & 0xff) << 8) | (buffer[index] & 0xff)));
                index += 2;

                if (index != (bufferSize - 1)) {
                    LOG.warning("Driver.readFile Ошибка в коде не соответствуют индексы");
                    break;
                }

                //Вывод в двоичном виде
//                System.out.println(Integer.toBinaryString(buffer[0] & 0xff));
            }
        } catch(IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Перевод данных из бинарного типа в float
     * @param buffer буфер с данными
     * @param index индекс
     * @param addIndex инкремент индекса
     * @return результат
     */
    private float readFloat(byte[] buffer, int index, int addIndex) {
        return Float.intBitsToFloat(
                ((buffer[index + addIndex + 3] & 0xff) << 24) |
                ((buffer[index + addIndex + 2] & 0xff) << 16) |
                ((buffer[index + addIndex + 1] & 0xff) << 8) |
                (buffer[index + addIndex] & 0xff));
    }

    /**
     * Перевод данных из бинарного типа в int
     * @param inputStream поток данных
     * @param bytesCount количество битов с данными
     * @return результат
     * @throws IOException ошибка
     */
    private int readInt(BufferedInputStream inputStream, int bytesCount) throws IOException {
        byte[] buffer = new byte[bytesCount];
        if (inputStream.read(buffer, 0, bytesCount) != -1) {
            switch(bytesCount) {
                case 1:
                    return (buffer[0] & 0xff);
                case 2:
                    return ((buffer[1] & 0xff) << 8) | (buffer[0] & 0xff);
                default:
                    throw new NullPointerException();
            }
        }
        throw new NullPointerException();
    }

    /**
     * Получение списка методов и их аннотаций в соответствии с конфигурацией
     * @return методы
     */
    private Map<String, String> getMethodsMap() {
        Map<String, String> result = new HashMap<>();
        Method[] method = Driver.class.getMethods();

        for(Method md: method){
            if (md.isAnnotationPresent(MCT20CounterParameter.class)) {
                result.put(md.getAnnotation(MCT20CounterParameter.class).name().getProperty(), md.getName());
            }
        }
        return result;
    }

    @MCT20CounterParameter(name = MCT20Config.TOTAL_TIME)
    public long getTotalTime() {
        return totalTime;
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME0)
    public float getWaterVoleme0() {
        return waterVolume[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME1)
    public float getWaterVoleme1() {
        return waterVolume[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME2)
    public float getWaterVoleme2() {
        return waterVolume[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME3)
    public float getWaterVoleme3() {
        return waterVolume[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME4)
    public float getWaterVoleme4() {
        return waterVolume[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME5)
    public float getWaterVoleme5() {
        return waterVolume[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME6)
    public float getWaterVoleme6() {
        return waterVolume[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME7)
    public float getWaterVoleme7() {
        return waterVolume[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT0)
    public float getWaterWeight0() {
        return waterWeight[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT1)
    public float getWaterWeight1() {
        return waterWeight[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT2)
    public float getWaterWeight2() {
        return waterWeight[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT3)
    public float getWaterWeight3() {
        return waterWeight[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT4)
    public float getWaterWeight4() {
        return waterWeight[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT5)
    public float getWaterWeight5() {
        return waterWeight[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT6)
    public float getWaterWeight6() {
        return waterWeight[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT7)
    public float getWaterWeight7() {
        return waterWeight[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER0)
    public float getWaterTemper0() {
        return waterTemper[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER1)
    public float getWaterTemper1() {
        return waterTemper[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER2)
    public float getWaterTemper2() {
        return waterTemper[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER3)
    public float getWaterTemper3() {
        return waterTemper[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER4)
    public float getWaterTemper4() {
        return waterTemper[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER5)
    public float getWaterTemper5() {
        return waterTemper[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER6)
    public float getWaterTemper6() {
        return waterTemper[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER7)
    public float getWaterTemper7() {
        return waterTemper[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE0)
    public float getWaterPressure0() {
        return waterPressure[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE1)
    public float getWaterPressure1() {
        return waterPressure[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE2)
    public float getWaterPressure2() {
        return waterPressure[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE3)
    public float getWaterPressure3() {
        return waterPressure[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE4)
    public float getWaterPressure4() {
        return waterPressure[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE5)
    public float getWaterPressure5() {
        return waterPressure[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE6)
    public float getWaterPressure6() {
        return waterPressure[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE7)
    public float getWaterPressure7() {
        return waterPressure[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT0)
    public float getWaterHeatAmount0() {
        return waterHeatAmount[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT1)
    public float getWaterHeatAmount1() {
        return waterHeatAmount[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT2)
    public float getWaterHeatAmount2() {
        return waterHeatAmount[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT3)
    public float getWaterHeatAmount3() {
        return waterHeatAmount[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT4)
    public float getWaterHeatAmount4() {
        return waterHeatAmount[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT5)
    public float getWaterHeatAmount5() {
        return waterHeatAmount[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT6)
    public float getWaterHeatAmount6() {
        return waterHeatAmount[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT7)
    public float getWaterHeatAmount7() {
        return waterHeatAmount[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED0)
    public float getWaterAccumulated0() {
        return waterAccumulated[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED1)
    public float getWaterAccumulated1() {
        return waterAccumulated[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED2)
    public float getWaterAccumulated2() {
        return waterAccumulated[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED3)
    public float getWaterAccumulated3() {
        return waterAccumulated[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED4)
    public float getWaterAccumulated4() {
        return waterAccumulated[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED5)
    public float getWaterAccumulated5() {
        return waterAccumulated[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED6)
    public float getWaterAccumulated6() {
        return waterAccumulated[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED7)
    public float getWaterAccumulated7() {
        return waterAccumulated[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED0)
    public float getWaterMassAccumulated0() {
        return waterMassAccumulated[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED1)
    public float getWaterMassAccumulated1() {
        return waterMassAccumulated[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED2)
    public float getWaterMassAccumulated2() {
        return waterMassAccumulated[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED3)
    public float getWaterMassAccumulated3() {
        return waterMassAccumulated[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED4)
    public float getWaterMassAccumulated4() {
        return waterMassAccumulated[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED5)
    public float getWaterMassAccumulated5() {
        return waterMassAccumulated[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED6)
    public float getWaterMassAccumulated6() {
        return waterMassAccumulated[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED7)
    public float getWaterMassAccumulated7() {
        return waterMassAccumulated[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED0)
    public float getWaterHeatAccumulated0() {
        return waterHeatAccumulated[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED1)
    public float getWaterHeatAccumulated1() {
        return waterHeatAccumulated[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED2)
    public float getWaterHeatAccumulated2() {
        return waterHeatAccumulated[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED3)
    public float getWaterHeatAccumulated3() {
        return waterHeatAccumulated[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED4)
    public float getWaterHeatAccumulated4() {
        return waterHeatAccumulated[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED5)
    public float getWaterHeatAccumulated5() {
        return waterHeatAccumulated[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED6)
    public float getWaterHeatAccumulated6() {
        return waterHeatAccumulated[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED7)
    public float getWaterHeatAccumulated7() {
        return waterHeatAccumulated[7];
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_GVS)
    public float getHeatAmountGVS() {
        return heatAmountGVS;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_CO)
    public float getHeatAmountCO() {
        return heatAmountCO;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_VENT)
    public float getHeatAmountVENT() {
        return heatAmountVENT;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_ACCUMULATED_GVS)
    public float getHeatAmountAccumulatedGVS() {
        return heatAmountAccumulatedGVS;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_ACCUMULATED_CO)
    public float getHeatAmountAccumulatedCO() {
        return heatAmountAccumulatedCO;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_ACCUMULATED_VENT)
    public float getHeatAmountAccumulatedVENT() {
        return heatAmountAccumulatedVENT;
    }

    @Override
    public String getURL() {
        return URL;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Driver{");
        sb.append("totalTime=").append(totalTime);
        sb.append(", waterVolume=");
        if (waterVolume == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterVolume.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterVolume[i]);
            sb.append(']');
        }
        sb.append(", waterWeight=");
        if (waterWeight == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterWeight.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterWeight[i]);
            sb.append(']');
        }
        sb.append(", waterTemper=");
        if (waterTemper == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterTemper.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterTemper[i]);
            sb.append(']');
        }
        sb.append(", waterPressure=");
        if (waterPressure == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterPressure.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterPressure[i]);
            sb.append(']');
        }
        sb.append(", waterHeatAmount=");
        if (waterHeatAmount == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterHeatAmount.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterHeatAmount[i]);
            sb.append(']');
        }
        sb.append(", waterAccumulated=");
        if (waterAccumulated == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterAccumulated.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterAccumulated[i]);
            sb.append(']');
        }
        sb.append(", waterMassAccumulated=");
        if (waterMassAccumulated == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterMassAccumulated.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterMassAccumulated[i]);
            sb.append(']');
        }
        sb.append(", waterHeatAccumulated=");
        if (waterHeatAccumulated == null) sb.append("null");
        else {
            sb.append('[');
            for (int i = 0; i < waterHeatAccumulated.length; ++i)
                sb.append(i == 0 ? "" : ", ").append(waterHeatAccumulated[i]);
            sb.append(']');
        }
        sb.append(", heatAmountGVS=").append(heatAmountGVS);
        sb.append(", heatAmountCO=").append(heatAmountCO);
        sb.append(", heatAmountVENT=").append(heatAmountVENT);
        sb.append(", heatAmountAccumulatedGVS=").append(heatAmountAccumulatedGVS);
        sb.append(", heatAmountAccumulatedCO=").append(heatAmountAccumulatedCO);
        sb.append(", heatAmountAccumulatedVENT=").append(heatAmountAccumulatedVENT);
        sb.append('}');
        return sb.toString();
    }
}
