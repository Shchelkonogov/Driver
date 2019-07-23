package ru.tecon.counter.MCT20;

import ru.tecon.counter.Counter;
import ru.tecon.counter.util.DriverLoadException;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;
import ru.tecon.counter.util.Drivers;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Driver implements Counter {

    private static final Logger LOG = Logger.getLogger(Driver.class.getName());

    private String url;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HH");

    private Long accumulatedTime;

    private Long totalTime;
    private Float[] waterVolume = new Float[8];
    private Float[] waterWeight = new Float[8];
    private Float[] waterTemper = new Float[8];
    private Float[] waterPressure = new Float[8];
    private Float[] waterHeatAmount = new Float[8];
    private Float[] waterAccumulated = new Float[8];
    private Float[] waterMassAccumulated = new Float[8];
    private Float[] waterHeatAccumulated = new Float[8];
    private Float heatAmountGVS;
    private Float heatAmountCO;
    private Float heatAmountVENT;
    private Float heatAmountAccumulatedGVS;
    private Float heatAmountAccumulatedCO;
    private Float heatAmountAccumulatedVENT;

    private Long offTime;
    private Long offTimeAccumulated;
    private Long[] stopTimeGError1 = new Long[8];
    private Long[] stopTimeGError2 = new Long[8];
    private Long[] stopTimeGError3 = new Long[8];
    private Long[] workingTimeG = new Long[8];
    private Long[] stopTimeT = new Long[8];
    private Long[] workingTimeT = new Long[8];
    private Long[] stopTimeP = new Long[8];
    private Long[] workingTimeP = new Long[8];
    private Long[] stopTimeMQ = new Long[8];
    private Long[] workingTimeMQ = new Long[8];
    private Long[] accumulatedStopTimeGError1 = new Long[8];
    private Long[] accumulatedStopTimeGError2 = new Long[8];
    private Long[] accumulatedStopTimeGError3 = new Long[8];
    private Long[] accumulatedWorkingTimeG = new Long[8];
    private Long[] accumulatedStopTimeMQ = new Long[8];
    private Long[] accumulatedWorkingTimeMQ = new Long[8];
    private Long[] currentStopTimeError1 = new Long[3];
    private Long[] currentStopTimeError2 = new Long[3];
    private Long[] workingTimeQ = new Long[3];
    private Float[] waterHeatZone = new Float[3];
    private Long[] accumulatedCurrentStopTimeError1 = new Long[3];
    private Long[] accumulatedCurrentStopTimeError2 = new Long[3];
    private Long[] accumulatedWorkingTimeQ = new Long[3];
    private Float[] accumulatedWaterHeatZone = new Float[3];

    public Driver() {
        try {
            Context ctx = new InitialContext();
            url = (String) ctx.lookup("java:comp/env/url");
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getConfig(String object) {
        return Stream.of(MCT20Config.values())
                .map(MCT20Config::getProperty)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getObjects() {
        List<String> objects = Drivers.scan(url, Arrays.asList("\\d{4}a\\d{8}-\\d{2}", "ans-\\d{8}-\\d{2}"));
        return objects.stream().map(e -> e = "МСТ-20-" + e).collect(Collectors.toList());
    }

    @Override
    public void clear() {
        Drivers.clear(this, url, Arrays.asList("\\d{4}a\\d{8}-\\d{2}", "ans-\\d{8}-\\d{2}"));
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        Collections.sort(params);

        String counterNumber = objectName.substring(objectName.length() - 4);
        String filePath = url + "/" + counterNumber.substring(0, 2) + "/" + counterNumber + "/";

        LocalDateTime date = params.get(0).getStartTime();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(3);

        Class<?> cl = this.getClass();
        Map<String, String> methodsMap = this.getMethodsMap();

        List<String> filesList = new ArrayList<>();
        while (date.isBefore(now)) {
            filesList.clear();
            LocalDateTime finalDate = date;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(filePath),
                    entry -> entry.getFileName().toString().matches("ans-" + finalDate.format(DATE_FORMAT))
                            || entry.getFileName().toString().matches(counterNumber + "a" + finalDate.format(DATE_FORMAT)))) {
                stream.forEach(e -> {
                    if (!Files.isDirectory(e)) {
                        filesList.add(e.toString());
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                for (String entry: filesList) {
                    LOG.info("Driver.loadData read file: " + entry);

                    readFile(entry);

                    for (DataModel model: params) {
                        if (!date.isBefore(model.getStartTime())) {
                            try {
                                String mName = methodsMap.get(model.getParamName());
                                if (Objects.nonNull(mName)) {
                                    Object value = cl.getMethod(mName).invoke(this);
                                    if (value == null) {
                                        continue;
                                    }
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
            } catch (DriverLoadException e) {
                LOG.warning(e.getMessage());
                if (e.getMessage().equals("IOException")) {
                    return;
                }
            }

            date = date.plusHours(1);
        }
    }

    /**
     * Метод парсит файл и выгружает из него значения
     * @param path путь к файлу
     * @throws DriverLoadException если произошла какая то ошибка при разборе
     */
    private void readFile(String path) throws DriverLoadException {
        LOG.info("Driver.readFile start read: " + path + " " + System.currentTimeMillis());
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(Paths.get(path), StandardOpenOption.READ))) {
            boolean head = true;

            int recordNumber = 1;

            while(inputStream.available() > 0) {
                if (head) {
                    //uint16_t lenA     uint8_t ver
                    if((readInt(inputStream, 2) != 6) || (readInt(inputStream, 1) != 3)) {
                        LOG.warning("Driver.readFile Неверный размер заголовка или версия протокола");
                        throw new DriverLoadException("Driver.readFile Неверный размер заголовка или версия протокола");
                    }
                    //uint16_t reqNumA
                    LOG.info("Driver.readFile Номер запроса: " + readInt(inputStream, 2));
                    //uint8_t reqRes
                    if(readInt(inputStream, 1) != 0) {
                        LOG.warning("Driver.readFile Результат обработки запроса выдал ошибку");
                        throw new DriverLoadException("Driver.readFile Результат обработки запроса выдал ошибку");
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
                        throw new DriverLoadException("Driver.readFile Неверное количество архивных записей");
                    }
                    //uint16_t crcA
                    LOG.info("Driver.readFile Контрольная сумма: " + readInt(inputStream, 2));
                    head = false;
                }

                switch (readInt(inputStream, 1)) {
                    case 3: {
                        recordNumber = load3(inputStream, recordNumber);
                        break;
                    }
                    case 4: {
                        recordNumber = load4(inputStream, recordNumber);
                        break;
                    }
                    default: {
                        LOG.warning("Driver.readFile Неверный формат архивной записи");
                        throw new DriverLoadException("Driver.readFile Неверный формат архивной записи");
                    }
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
            throw new DriverLoadException("IOException");
        }
    }

    /**
     * Всевозможные проверки содержимого файла
     * @param inputStream поток данных
     * @param buffer буфер куда читаются данные
     * @param bufferSize размерность буфера
     * @throws IOException если проблемы с потоком данных
     * @throws DriverLoadException если не прошли проверки
     */
    private void check(BufferedInputStream inputStream, byte[] buffer, int bufferSize) throws IOException, DriverLoadException {
        if (inputStream.available() < bufferSize) {
            LOG.warning("Driver.load3 Ошибка в данных");
            throw new DriverLoadException("Driver.load3 Ошибка в данных");
        }

        if (inputStream.read(buffer, 0, bufferSize) == -1) {
            LOG.warning("Driver.load3 Ошибка в чтение данных");
            throw new DriverLoadException("Driver.load3 Ошибка в чтение данных");
        }

        if ((buffer[bufferSize - 1] & 0xff) != 10) {
            LOG.warning("Driver.load3 Ошибочное окончание записи");
            throw new DriverLoadException("Driver.load3 Ошибочное окончание записи");
        }
    }

    /**
     * 3 версия файла
     * @param inputStream поток данных
     * @param recordNumber номер строки (в файле 11 кусков, нужен только 11 кусок)
     *                     для пропуска 10 первых кусков
     * @return номер строки, что бы обновить его в вызываемом методе
     * @throws IOException если ошибки с потоком данных
     * @throws DriverLoadException если ошибки в проверке
     */
    private int load3(BufferedInputStream inputStream, int recordNumber) throws IOException, DriverLoadException {
        int  bufferSize = 384;
        byte[] buffer = new byte[bufferSize];

        check(inputStream, buffer, bufferSize);

        if (recordNumber != 11) {
            return ++recordNumber;
        }

//        System.out.println("ХЗ ЧТО ЭТО: " + (buffer[0] & 0xff));
//        System.out.println("ХЗ ЧТО ЭТО: " + (buffer[1] & 0xff));
//        System.out.println("ХЗ ЧТО ЭТО: " + (buffer[2] & 0xff));
//
//        System.out.println("Год: 20" + (buffer[3] & 0xff));
//        System.out.println("Месяц: " + (buffer[4] & 0xff));
//        System.out.println("День: " + (buffer[5] & 0xff));
//        System.out.println("Час: " + (buffer[6] & 0xff));

        totalTime = readLong(buffer, 7, 0);
        accumulatedTime = readLong(buffer, 11, 0);

        int index = 15;
        for(int i = 0; i < 8; i++) {
            waterVolume[i] = readFloat(buffer, index, 0);

            waterWeight[i] = readFloat(buffer, index, 32);

            waterTemper[i] = readFloat(buffer, index, 64);

            waterPressure[i] = readFloat(buffer, index, 96);

            waterHeatAmount[i] = readFloat(buffer, index, 128);

//            System.out.println("Диагностика измерения объема воды за время канал " + i + ":" +
//                    (((buffer[index + 160 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 160 - (2 * i)] & 0xff)));
//            System.out.println("Диагностика измерения массы воды за время канал " + i + ":" +
//                    (((buffer[index + 176 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 176 - (2 * i)] & 0xff)));
//            System.out.println("Диагностика измерения температуры за время канал " + i + ":" +
//                    (((buffer[index + 192 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 192 - (2 * i)] & 0xff)));
//            System.out.println("Диагностика давления за время канал " + i + ":" +
//                    (((buffer[index + 208 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 208 - (2 * i)] & 0xff)));
//            System.out.println("Диагностика расчета тепла за время канал " + i + ":" +
//                    (((buffer[index + 224 - (2 * i) + 1] & 0xff) << 8) | (buffer[index + 224 - (2 * i)] & 0xff)));

            waterAccumulated[i] = readFloat(buffer, index, 240);

            waterMassAccumulated[i] = readFloat(buffer, index, 272);

            waterHeatAccumulated[i] = readFloat(buffer, index, 304);

            index += 4;
        }
        index += 304;

//        System.out.println("Диагностика расчетов по схеме 1: " +
//                (((buffer[index + 3] & 0xff) << 24) |
//                ((buffer[index + 2] & 0xff) << 16) |
//                ((buffer[index + 1] & 0xff) << 8) |
//                (buffer[index] & 0xff)));
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

//        System.out.println("Диагностика прибора: " + (((buffer[index + 1] & 0xff) << 8) | (buffer[index] & 0xff)));
        index += 2;

//        System.out.println("Контрольная сумма записи: " + (((buffer[index + 1] & 0xff) << 8) | (buffer[index] & 0xff)));
        index += 2;

        if (index != (bufferSize - 1)) {
            LOG.warning("Driver.load3 Ошибка в коде не соответствуют индексы");
            throw new DriverLoadException("Driver.load3 Ошибка в коде не соответствуют индексы");
        }

        //Вывод в двоичном виде
//        System.out.println(Integer.toBinaryString(buffer[0] & 0xff));
        return recordNumber;
    }

    /**
     * 4 версия файла
     * @param inputStream поток данных
     * @param recordNumber номер строки (в файле 11 кусков, нужен только 11 кусок)
     *                     для пропуска 10 первых кусков
     * @return номер строки, что бы обновить его в вызываемом методе
     * @throws IOException если ошибки с потоком данных
     * @throws DriverLoadException если ошибки в проверке
     */
    private int load4(BufferedInputStream inputStream, int recordNumber) throws IOException, DriverLoadException {
        int  bufferSize = 888;
        byte[] buffer = new byte[bufferSize];

        check(inputStream, buffer, bufferSize);

        if (recordNumber != 11) {
            return ++recordNumber;
        }

//        System.out.println("ХЗ ЧТО ЭТО: " + (buffer[0] & 0xff));
//        System.out.println("ХЗ ЧТО ЭТО: " + (buffer[1] & 0xff));
//        System.out.println("ХЗ ЧТО ЭТО: " + (buffer[2] & 0xff));
//
//        System.out.println("Год: 20" + (buffer[3] & 0xff));
//        System.out.println("Месяц: " + (buffer[4] & 0xff));
//        System.out.println("День: " + (buffer[5] & 0xff));
//        System.out.println("Час: " + (buffer[6] & 0xff));

        totalTime = readLong(buffer, 7, 0);
        offTime = readLong(buffer, 11, 0);
        offTimeAccumulated = readLong(buffer, 15, 0);

        int index = 19;
        for (int i = 0; i < 4; i++) {
            stopTimeGError1[2 * i] = readLong(buffer, index, 0);
            stopTimeGError2[2 * i] = readLong(buffer, index, 4);
            stopTimeGError3[2 * i] = readLong(buffer, index, 8);
            workingTimeG[2 * i] = readLong(buffer, index, 12);
            waterVolume[2 * i] = readFloat(buffer, index, 16);

            stopTimeT[2 * i] = readLong(buffer, index, 20);
            workingTimeT[2 * i] = readLong(buffer, index, 24);
            waterTemper[2 * i] = readFloat(buffer, index, 28);

            stopTimeP[2 * i] = readLong(buffer, index, 32);
            workingTimeP[2 * i] = readLong(buffer, index, 36);
            waterPressure[2 * i] = readFloat(buffer, index, 40);

            stopTimeMQ[2 * i] = readLong(buffer, index, 44);
            workingTimeMQ[2 * i] = readLong(buffer, index, 48);
            waterWeight[2 * i] = readFloat(buffer, index, 52);
            waterHeatAmount[2 * i] = readFloat(buffer, index, 56);

            accumulatedStopTimeGError1[2 * i] = readLong(buffer, index, 60);
            accumulatedStopTimeGError2[2 * i] = readLong(buffer, index, 64);
            accumulatedStopTimeGError3[2 * i] = readLong(buffer, index, 68);
            accumulatedWorkingTimeG[2 * i] = readLong(buffer, index, 72);
            waterAccumulated[2 * i] = readFloat(buffer, index, 76);
            accumulatedStopTimeMQ[2 * i] = readLong(buffer, index, 80);
            accumulatedWorkingTimeMQ[2 * i] = readLong(buffer, index, 84);
            waterMassAccumulated[2 * i] = readFloat(buffer, index, 88);
            waterHeatAccumulated[2 * i] = readFloat(buffer, index, 92);

            stopTimeGError1[(2 * i) + 1] = readLong(buffer, index, 96);
            stopTimeGError2[(2 * i) + 1] = readLong(buffer, index, 100);
            stopTimeGError3[(2 * i) + 1] = readLong(buffer, index, 104);
            workingTimeG[(2 * i) + 1] = readLong(buffer, index, 108);
            waterVolume[(2 * i) + 1] = readFloat(buffer, index, 112);

            stopTimeT[(2 * i) + 1] = readLong(buffer, index, 116);
            workingTimeT[(2 * i) + 1] = readLong(buffer, index, 120);
            waterTemper[(2 * i) + 1] = readFloat(buffer, index, 124);

            stopTimeP[(2 * i) + 1] = readLong(buffer, index, 128);
            workingTimeP[(2 * i) + 1] = readLong(buffer, index, 132);
            waterPressure[(2 * i) + 1] = readFloat(buffer, index, 136);

            stopTimeMQ[(2 * i) + 1] = readLong(buffer, index, 140);
            workingTimeMQ[(2 * i) + 1] = readLong(buffer, index, 144);
            waterWeight[(2 * i) + 1] = readFloat(buffer, index, 148);
            waterHeatAmount[(2 * i) + 1] = readFloat(buffer, index, 152);

            accumulatedStopTimeGError1[(2 * i) + 1] = readLong(buffer, index, 156);
            accumulatedStopTimeGError2[(2 * i) + 1] = readLong(buffer, index, 160);
            accumulatedStopTimeGError3[(2 * i) + 1] = readLong(buffer, index, 164);
            accumulatedWorkingTimeG[(2 * i) + 1] = readLong(buffer, index, 168);
            waterAccumulated[(2 * i) + 1] = readFloat(buffer, index, 172);
            accumulatedStopTimeMQ[(2 * i) + 1] = readLong(buffer, index, 176);
            accumulatedWorkingTimeMQ[(2 * i) + 1] = readLong(buffer, index, 180);
            waterMassAccumulated[(2 * i) + 1] = readFloat(buffer, index, 184);
            waterHeatAccumulated[(2 * i) + 1] = readFloat(buffer, index, 188);

            if (i < 3) {
                currentStopTimeError1[i] = readLong(buffer, index, 192);
                currentStopTimeError2[i] = readLong(buffer, index, 196);
                workingTimeQ[i] = readLong(buffer, index, 200);
                waterHeatZone[i] = readFloat(buffer, index, 204);

                accumulatedCurrentStopTimeError1[i] = readLong(buffer, index, 208);
                accumulatedCurrentStopTimeError2[i] = readLong(buffer, index, 212);
                accumulatedWorkingTimeQ[i] = readLong(buffer, index, 216);
                accumulatedWaterHeatZone[i] = readFloat(buffer, index, 220);
                index += 224;
            } else {
                index += 192;
            }
        }

//        System.out.println("Диагностика прибора: " + (((buffer[index + 1] & 0xff) << 8) | (buffer[index] & 0xff)));
        index += 2;

//        System.out.println("Контрольная сумма записи: " + (((buffer[index + 1] & 0xff) << 8) | (buffer[index] & 0xff)));
        index += 2;

        if (index != (bufferSize - 1)) {
            LOG.warning("Driver.load3 Ошибка в коде не соответствуют индексы");
            throw new DriverLoadException("Driver.load3 Ошибка в коде не соответствуют индексы");
        }
        return recordNumber;
    }

    /**
     * Печать в консоль данных теплосчетчика
     * @param path путь к файлу с данными
     */
    public void printData(String path) {
        try {
            this.readFile(path);
            System.out.println(this);
        } catch (DriverLoadException e) {
            LOG.warning(e.getMessage());
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
     * Перевод данных из бинарного типа в long
     * @param buffer буфер с данными
     * @param index индекс
     * @param addIndex инкремент индекса
     * @return результат
     */
    private long readLong(byte[] buffer, int index, int addIndex) {
        return ((buffer[index + addIndex + 3] & 0xff) << 24) |
                    ((buffer[index + addIndex + 2] & 0xff) << 16) |
                    ((buffer[index + addIndex + 1] & 0xff) << 8) |
                    (buffer[index + addIndex] & 0xff);
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
        Method[] method = this.getClass().getMethods();

        for(Method md: method){
            if (md.isAnnotationPresent(MCT20CounterParameter.class)) {
                result.put(md.getAnnotation(MCT20CounterParameter.class).name().getProperty(), md.getName());
            }
        }
        return result;
    }

    public String getUrl() {
        return url;
    }

    @MCT20CounterParameter(name = MCT20Config.TOTAL_TIME)
    public Long getTotalTime() {
        return totalTime;
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME0)
    public Float getWaterVoleme0() {
        return waterVolume[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME1)
    public Float getWaterVoleme1() {
        return waterVolume[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME2)
    public Float getWaterVoleme2() {
        return waterVolume[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME3)
    public Float getWaterVoleme3() {
        return waterVolume[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME4)
    public Float getWaterVoleme4() {
        return waterVolume[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME5)
    public Float getWaterVoleme5() {
        return waterVolume[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME6)
    public Float getWaterVoleme6() {
        return waterVolume[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_VOLUME7)
    public Float getWaterVoleme7() {
        return waterVolume[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT0)
    public Float getWaterWeight0() {
        return waterWeight[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT1)
    public Float getWaterWeight1() {
        return waterWeight[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT2)
    public Float getWaterWeight2() {
        return waterWeight[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT3)
    public Float getWaterWeight3() {
        return waterWeight[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT4)
    public Float getWaterWeight4() {
        return waterWeight[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT5)
    public Float getWaterWeight5() {
        return waterWeight[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT6)
    public Float getWaterWeight6() {
        return waterWeight[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_WEIGHT7)
    public Float getWaterWeight7() {
        return waterWeight[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER0)
    public Float getWaterTemper0() {
        return waterTemper[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER1)
    public Float getWaterTemper1() {
        return waterTemper[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER2)
    public Float getWaterTemper2() {
        return waterTemper[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER3)
    public Float getWaterTemper3() {
        return waterTemper[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER4)
    public Float getWaterTemper4() {
        return waterTemper[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER5)
    public Float getWaterTemper5() {
        return waterTemper[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER6)
    public Float getWaterTemper6() {
        return waterTemper[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_TEMPER7)
    public Float getWaterTemper7() {
        return waterTemper[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE0)
    public Float getWaterPressure0() {
        return waterPressure[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE1)
    public Float getWaterPressure1() {
        return waterPressure[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE2)
    public Float getWaterPressure2() {
        return waterPressure[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE3)
    public Float getWaterPressure3() {
        return waterPressure[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE4)
    public Float getWaterPressure4() {
        return waterPressure[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE5)
    public Float getWaterPressure5() {
        return waterPressure[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE6)
    public Float getWaterPressure6() {
        return waterPressure[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_PRESSURE7)
    public Float getWaterPressure7() {
        return waterPressure[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT0)
    public Float getWaterHeatAmount0() {
        return waterHeatAmount[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT1)
    public Float getWaterHeatAmount1() {
        return waterHeatAmount[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT2)
    public Float getWaterHeatAmount2() {
        return waterHeatAmount[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT3)
    public Float getWaterHeatAmount3() {
        return waterHeatAmount[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT4)
    public Float getWaterHeatAmount4() {
        return waterHeatAmount[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT5)
    public Float getWaterHeatAmount5() {
        return waterHeatAmount[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT6)
    public Float getWaterHeatAmount6() {
        return waterHeatAmount[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_AMOUNT7)
    public Float getWaterHeatAmount7() {
        return waterHeatAmount[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED0)
    public Float getWaterAccumulated0() {
        return waterAccumulated[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED1)
    public Float getWaterAccumulated1() {
        return waterAccumulated[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED2)
    public Float getWaterAccumulated2() {
        return waterAccumulated[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED3)
    public Float getWaterAccumulated3() {
        return waterAccumulated[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED4)
    public Float getWaterAccumulated4() {
        return waterAccumulated[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED5)
    public Float getWaterAccumulated5() {
        return waterAccumulated[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED6)
    public Float getWaterAccumulated6() {
        return waterAccumulated[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_ACCUMULATED7)
    public Float getWaterAccumulated7() {
        return waterAccumulated[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED0)
    public Float getWaterMassAccumulated0() {
        return waterMassAccumulated[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED1)
    public Float getWaterMassAccumulated1() {
        return waterMassAccumulated[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED2)
    public Float getWaterMassAccumulated2() {
        return waterMassAccumulated[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED3)
    public Float getWaterMassAccumulated3() {
        return waterMassAccumulated[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED4)
    public Float getWaterMassAccumulated4() {
        return waterMassAccumulated[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED5)
    public Float getWaterMassAccumulated5() {
        return waterMassAccumulated[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED6)
    public Float getWaterMassAccumulated6() {
        return waterMassAccumulated[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_MASS_ACCUMULATED7)
    public Float getWaterMassAccumulated7() {
        return waterMassAccumulated[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED0)
    public Float getWaterHeatAccumulated0() {
        return waterHeatAccumulated[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED1)
    public Float getWaterHeatAccumulated1() {
        return waterHeatAccumulated[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED2)
    public Float getWaterHeatAccumulated2() {
        return waterHeatAccumulated[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED3)
    public Float getWaterHeatAccumulated3() {
        return waterHeatAccumulated[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED4)
    public Float getWaterHeatAccumulated4() {
        return waterHeatAccumulated[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED5)
    public Float getWaterHeatAccumulated5() {
        return waterHeatAccumulated[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED6)
    public Float getWaterHeatAccumulated6() {
        return waterHeatAccumulated[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ACCUMULATED7)
    public Float getWaterHeatAccumulated7() {
        return waterHeatAccumulated[7];
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_GVS)
    public Float getHeatAmountGVS() {
        return heatAmountGVS;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_CO)
    public Float getHeatAmountCO() {
        return heatAmountCO;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_VENT)
    public Float getHeatAmountVENT() {
        return heatAmountVENT;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_ACCUMULATED_GVS)
    public Float getHeatAmountAccumulatedGVS() {
        return heatAmountAccumulatedGVS;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_ACCUMULATED_CO)
    public Float getHeatAmountAccumulatedCO() {
        return heatAmountAccumulatedCO;
    }

    @MCT20CounterParameter(name = MCT20Config.HEAT_AMOUNT_ACCUMULATED_VENT)
    public Float getHeatAmountAccumulatedVENT() {
        return heatAmountAccumulatedVENT;
    }

    @MCT20CounterParameter(name = MCT20Config.OFF_TIME)
    public Long getOffTime() {
        return offTime;
    }

    @MCT20CounterParameter(name = MCT20Config.OFF_TIME_ACCUMULATED)
    public Long getOffTimeAccumulated() {
        return offTimeAccumulated;
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_0)
    public Long getStopTimeGError10() {
        return stopTimeGError1[0];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_1)
    public Long getStopTimeGError11() {
        return stopTimeGError1[1];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_2)
    public Long getStopTimeGError12() {
        return stopTimeGError1[2];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_3)
    public Long getStopTimeGError13() {
        return stopTimeGError1[3];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_4)
    public Long getStopTimeGError14() {
        return stopTimeGError1[4];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_5)
    public Long getStopTimeGError15() {
        return stopTimeGError1[5];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_6)
    public Long getStopTimeGError16() {
        return stopTimeGError1[6];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_1_CHANEL_7)
    public Long getStopTimeGError17() {
        return stopTimeGError1[7];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_0)
    public Long getStopTimeGError20() {
        return stopTimeGError2[0];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_1)
    public Long getStopTimeGError21() {
        return stopTimeGError2[1];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_2)
    public Long getStopTimeGError22() {
        return stopTimeGError2[2];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_3)
    public Long getStopTimeGError23() {
        return stopTimeGError2[3];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_4)
    public Long getStopTimeGError24() {
        return stopTimeGError2[4];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_5)
    public Long getStopTimeGError25() {
        return stopTimeGError2[5];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_6)
    public Long getStopTimeGError26() {
        return stopTimeGError2[6];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_2_CHANEL_7)
    public Long getStopTimeGError27() {
        return stopTimeGError2[7];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_0)
    public Long getStopTimeGError30() {
        return stopTimeGError3[0];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_1)
    public Long getStopTimeGError31() {
        return stopTimeGError3[1];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_2)
    public Long getStopTimeGError32() {
        return stopTimeGError3[2];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_3)
    public Long getStopTimeGError33() {
        return stopTimeGError3[3];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_4)
    public Long getStopTimeGError34() {
        return stopTimeGError3[4];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_5)
    public Long getStopTimeGError35() {
        return stopTimeGError3[5];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_6)
    public Long getStopTimeGError36() {
        return stopTimeGError3[6];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_G_ERROR_3_CHANEL_7)
    public Long getStopTimeGError37() {
        return stopTimeGError3[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_0)
    public Long getWorkingTimeG0() {
        return workingTimeG[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_1)
    public Long getWorkingTimeG1() {
        return workingTimeG[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_2)
    public Long getWorkingTimeG2() {
        return workingTimeG[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_3)
    public Long getWorkingTimeG3() {
        return workingTimeG[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_4)
    public Long getWorkingTimeG4() {
        return workingTimeG[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_5)
    public Long getWorkingTimeG5() {
        return workingTimeG[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_6)
    public Long getWorkingTimeG6() {
        return workingTimeG[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_G_7)
    public Long getWorkingTimeG7() {
        return workingTimeG[7];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_0)
    public Long getStopTimeT0() {
        return stopTimeT[0];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_1)
    public Long getStopTimeT1() {
        return stopTimeT[1];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_2)
    public Long getStopTimeT2() {
        return stopTimeT[2];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_3)
    public Long getStopTimeT3() {
        return stopTimeT[3];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_4)
    public Long getStopTimeT4() {
        return stopTimeT[4];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_5)
    public Long getStopTimeT5() {
        return stopTimeT[5];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_6)
    public Long getStopTimeT6() {
        return stopTimeT[6];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_T_7)
    public Long getStopTimeT7() {
        return stopTimeT[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_0)
    public Long getWorkingTimeT0() {
        return workingTimeT[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_1)
    public Long getWorkingTimeT1() {
        return workingTimeT[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_2)
    public Long getWorkingTimeT2() {
        return workingTimeT[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_3)
    public Long getWorkingTimeT3() {
        return workingTimeT[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_4)
    public Long getWorkingTimeT4() {
        return workingTimeT[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_5)
    public Long getWorkingTimeT5() {
        return workingTimeT[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_6)
    public Long getWorkingTimeT6() {
        return workingTimeT[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_T_7)
    public Long getWorkingTimeT7() {
        return workingTimeT[7];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_0)
    public Long getStopTimeP0() {
        return stopTimeP[0];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_1)
    public Long getStopTimeP1() {
        return stopTimeP[1];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_2)
    public Long getStopTimeP2() {
        return stopTimeP[2];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_3)
    public Long getStopTimeP3() {
        return stopTimeP[3];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_4)
    public Long getStopTimeP4() {
        return stopTimeP[4];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_5)
    public Long getStopTimeP5() {
        return stopTimeP[5];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_6)
    public Long getStopTimeP6() {
        return stopTimeP[6];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_P_7)
    public Long getStopTimeP7() {
        return stopTimeP[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_0)
    public Long getWorkingTimeP0() {
        return workingTimeP[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_1)
    public Long getWorkingTimeP1() {
        return workingTimeP[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_2)
    public Long getWorkingTimeP2() {
        return workingTimeP[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_3)
    public Long getWorkingTimeP3() {
        return workingTimeP[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_4)
    public Long getWorkingTimeP4() {
        return workingTimeP[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_5)
    public Long getWorkingTimeP5() {
        return workingTimeP[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_6)
    public Long getWorkingTimeP6() {
        return workingTimeP[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_P_7)
    public Long getWorkingTimeP7() {
        return workingTimeP[7];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_0)
    public Long getStopTimeMQ0() {
        return stopTimeMQ[0];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_1)
    public Long getStopTimeMQ1() {
        return stopTimeMQ[1];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_2)
    public Long getStopTimeMQ2() {
        return stopTimeMQ[2];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_3)
    public Long getStopTimeMQ3() {
        return stopTimeMQ[3];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_4)
    public Long getStopTimeMQ4() {
        return stopTimeMQ[4];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_5)
    public Long getStopTimeMQ5() {
        return stopTimeMQ[5];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_6)
    public Long getStopTimeMQ6() {
        return stopTimeMQ[6];
    }

    @MCT20CounterParameter(name = MCT20Config.STOP_TIME_MQ_7)
    public Long getStopTimeMQ7() {
        return stopTimeMQ[7];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_0)
    public Long getWorkingTimeMQ0() {
        return workingTimeMQ[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_1)
    public Long getWorkingTimeMQ1() {
        return workingTimeMQ[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_2)
    public Long getWorkingTimeMQ2() {
        return workingTimeMQ[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_3)
    public Long getWorkingTimeMQ3() {
        return workingTimeMQ[3];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_4)
    public Long getWorkingTimeMQ4() {
        return workingTimeMQ[4];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_5)
    public Long getWorkingTimeMQ5() {
        return workingTimeMQ[5];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_6)
    public Long getWorkingTimeMQ6() {
        return workingTimeMQ[6];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_MQ_7)
    public Long getWorkingTimeMQ7() {
        return workingTimeMQ[7];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_0)
    public Long getAccumulatedStopTimeGError10() {
        return accumulatedStopTimeGError1[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_1)
    public Long getAccumulatedStopTimeGError11() {
        return accumulatedStopTimeGError1[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_2)
    public Long getAccumulatedStopTimeGError12() {
        return accumulatedStopTimeGError1[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_3)
    public Long getAccumulatedStopTimeGError13() {
        return accumulatedStopTimeGError1[3];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_4)
    public Long getAccumulatedStopTimeGError14() {
        return accumulatedStopTimeGError1[4];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_5)
    public Long getAccumulatedStopTimeGError15() {
        return accumulatedStopTimeGError1[5];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_6)
    public Long getAccumulatedStopTimeGError16() {
        return accumulatedStopTimeGError1[6];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_1_CHANEL_7)
    public Long getAccumulatedStopTimeGError17() {
        return accumulatedStopTimeGError1[7];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_0)
    public Long getAccumulatedStopTimeGError20() {
        return accumulatedStopTimeGError2[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_1)
    public Long getAccumulatedStopTimeGError21() {
        return accumulatedStopTimeGError2[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_2)
    public Long getAccumulatedStopTimeGError22() {
        return accumulatedStopTimeGError2[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_3)
    public Long getAccumulatedStopTimeGError23() {
        return accumulatedStopTimeGError2[3];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_4)
    public Long getAccumulatedStopTimeGError24() {
        return accumulatedStopTimeGError2[4];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_5)
    public Long getAccumulatedStopTimeGError25() {
        return accumulatedStopTimeGError2[5];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_6)
    public Long getAccumulatedStopTimeGError26() {
        return accumulatedStopTimeGError2[6];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_2_CHANEL_7)
    public Long getAccumulatedStopTimeGError27() {
        return accumulatedStopTimeGError2[7];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_0)
    public Long getAccumulatedStopTimeGError30() {
        return accumulatedStopTimeGError3[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_1)
    public Long getAccumulatedStopTimeGError31() {
        return accumulatedStopTimeGError3[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_2)
    public Long getAccumulatedStopTimeGError32() {
        return accumulatedStopTimeGError3[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_3)
    public Long getAccumulatedStopTimeGError33() {
        return accumulatedStopTimeGError3[3];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_4)
    public Long getAccumulatedStopTimeGError34() {
        return accumulatedStopTimeGError3[4];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_5)
    public Long getAccumulatedStopTimeGError35() {
        return accumulatedStopTimeGError3[5];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_6)
    public Long getAccumulatedStopTimeGError36() {
        return accumulatedStopTimeGError3[6];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_G_ERROR_3_CHANEL_7)
    public Long getAccumulatedStopTimeGError37() {
        return accumulatedStopTimeGError3[7];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_0)
    public Long getAccumulatedWorkingTimeG0() {
        return accumulatedWorkingTimeG[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_1)
    public Long getAccumulatedWorkingTimeG1() {
        return accumulatedWorkingTimeG[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_2)
    public Long getAccumulatedWorkingTimeG2() {
        return accumulatedWorkingTimeG[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_3)
    public Long getAccumulatedWorkingTimeG3() {
        return accumulatedWorkingTimeG[3];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_4)
    public Long getAccumulatedWorkingTimeG4() {
        return accumulatedWorkingTimeG[4];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_5)
    public Long getAccumulatedWorkingTimeG5() {
        return accumulatedWorkingTimeG[5];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_6)
    public Long getAccumulatedWorkingTimeG6() {
        return accumulatedWorkingTimeG[6];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_G_7)
    public Long getAccumulatedWorkingTimeG7() {
        return accumulatedWorkingTimeG[7];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_0)
    public Long getAccumulatedStopTimeMQ0() {
        return accumulatedStopTimeMQ[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_1)
    public Long getAccumulatedStopTimeMQ1() {
        return accumulatedStopTimeMQ[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_2)
    public Long getAccumulatedStopTimeMQ2() {
        return accumulatedStopTimeMQ[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_3)
    public Long getAccumulatedStopTimeMQ3() {
        return accumulatedStopTimeMQ[3];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_4)
    public Long getAccumulatedStopTimeMQ4() {
        return accumulatedStopTimeMQ[4];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_5)
    public Long getAccumulatedStopTimeMQ5() {
        return accumulatedStopTimeMQ[5];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_6)
    public Long getAccumulatedStopTimeMQ6() {
        return accumulatedStopTimeMQ[6];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_STOP_TIME_MQ_7)
    public Long getAccumulatedStopTimeMQ7() {
        return accumulatedStopTimeMQ[7];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_0)
    public Long getAccumulatedWorkingTimeMQ0() {
        return accumulatedWorkingTimeMQ[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_1)
    public Long getAccumulatedWorkingTimeMQ1() {
        return accumulatedWorkingTimeMQ[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_2)
    public Long getAccumulatedWorkingTimeMQ2() {
        return accumulatedWorkingTimeMQ[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_3)
    public Long getAccumulatedWorkingTimeMQ3() {
        return accumulatedWorkingTimeMQ[3];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_4)
    public Long getAccumulatedWorkingTimeMQ4() {
        return accumulatedWorkingTimeMQ[4];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_5)
    public Long getAccumulatedWorkingTimeMQ5() {
        return accumulatedWorkingTimeMQ[5];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_6)
    public Long getAccumulatedWorkingTimeMQ6() {
        return accumulatedWorkingTimeMQ[6];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_MQ_7)
    public Long getAccumulatedWorkingTimeMQ7() {
        return accumulatedWorkingTimeMQ[7];
    }

    @MCT20CounterParameter(name = MCT20Config.CURRENT_STOP_TIME_ERROR_1_ZONE_0)
    public Long getCurrentStopTimeError10() {
        return currentStopTimeError1[0];
    }

    @MCT20CounterParameter(name = MCT20Config.CURRENT_STOP_TIME_ERROR_1_ZONE_1)
    public Long getCurrentStopTimeError11() {
        return currentStopTimeError1[1];
    }

    @MCT20CounterParameter(name = MCT20Config.CURRENT_STOP_TIME_ERROR_1_ZONE_2)
    public Long getCurrentStopTimeError12() {
        return currentStopTimeError1[2];
    }

    @MCT20CounterParameter(name = MCT20Config.CURRENT_STOP_TIME_ERROR_1_ZONE_0)
    public Long getCurrentStopTimeError20() {
        return currentStopTimeError2[0];
    }

    @MCT20CounterParameter(name = MCT20Config.CURRENT_STOP_TIME_ERROR_1_ZONE_1)
    public Long getCurrentStopTimeError21() {
        return currentStopTimeError2[1];
    }

    @MCT20CounterParameter(name = MCT20Config.CURRENT_STOP_TIME_ERROR_1_ZONE_2)
    public Long getCurrentStopTimeError22() {
        return currentStopTimeError2[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_Q_ZONE_0)
    public Long getWorkingTimeQ0() {
        return workingTimeQ[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_Q_ZONE_1)
    public Long getWorkingTimeQ1() {
        return workingTimeQ[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WORKING_TIME_Q_ZONE_2)
    public Long getWorkingTimeQ2() {
        return workingTimeQ[2];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ZONE_0)
    public Float getWaterHeatZone0() {
        return waterHeatZone[0];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ZONE_1)
    public Float getWaterHeatZone1() {
        return waterHeatZone[1];
    }

    @MCT20CounterParameter(name = MCT20Config.WATER_HEAT_ZONE_2)
    public Float getWaterHeatZone2() {
        return waterHeatZone[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_0)
    public Long getAccumulatedCurrentStopTimeError10() {
        return accumulatedCurrentStopTimeError1[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_1)
    public Long getAccumulatedCurrentStopTimeError11() {
        return accumulatedCurrentStopTimeError1[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_CURRENT_STOP_TIME_ERROR_1_ZONE_2)
    public Long getAccumulatedCurrentStopTimeError12() {
        return accumulatedCurrentStopTimeError1[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_0)
    public Long getAccumulatedCurrentStopTimeError20() {
        return accumulatedCurrentStopTimeError2[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_1)
    public Long getAccumulatedCurrentStopTimeError21() {
        return accumulatedCurrentStopTimeError2[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_CURRENT_STOP_TIME_ERROR_2_ZONE_2)
    public Long getAccumulatedCurrentStopTimeError22() {
        return accumulatedCurrentStopTimeError2[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_Q_ZONE_0)
    public Long getAccumulatedWorkingTimeQ0() {
        return accumulatedWorkingTimeQ[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_Q_ZONE_1)
    public Long getAccumulatedWorkingTimeQ1() {
        return accumulatedWorkingTimeQ[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WORKING_TIME_Q_ZONE_2)
    public Long getAccumulatedWorkingTimeQ2() {
        return accumulatedWorkingTimeQ[2];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WATER_HEAT_ZONE_0)
    public Float getAccumulatedWaterHeatZone0() {
        return accumulatedWaterHeatZone[0];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WATER_HEAT_ZONE_1)
    public Float getAccumulatedWaterHeatZone1() {
        return accumulatedWaterHeatZone[1];
    }

    @MCT20CounterParameter(name = MCT20Config.ACCUMULATED_WATER_HEAT_ZONE_2)
    public Float getAccumulatedWaterHeatZone2() {
        return accumulatedWaterHeatZone[2];
    }

    @Override
    public String toString() {
        return new StringJoiner(",\n", Driver.class.getSimpleName() + "[\n", "\n]")
                .add("  accumulatedTime (Время накопления данных, сек) = " + accumulatedTime)
                .add("  totalTime (Общее время работы) = " + totalTime)
                .add("  waterVolume (Объем воды за время) = " + Arrays.toString(waterVolume))
                .add("  waterWeight (Масса воды за время) = " + Arrays.toString(waterWeight))
                .add("  waterTemper (Температура за время) = " + Arrays.toString(waterTemper))
                .add("  waterPressure (Давление используемое в рассчетах) = " + Arrays.toString(waterPressure))
                .add("  waterHeatAmount (Количество тепла за время) = " + Arrays.toString(waterHeatAmount))
                .add("  waterAccumulated (Объем воды накопленный) = " + Arrays.toString(waterAccumulated))
                .add("  waterMassAccumulated (Масса воды накопленная) = " + Arrays.toString(waterMassAccumulated))
                .add("  waterHeatAccumulated (Количество тепла накопленное) = " + Arrays.toString(waterHeatAccumulated))
                .add("  heatAmountGVS (Количество теплоты в системе ГВС) = " + heatAmountGVS)
                .add("  heatAmountCO (Количество теплоты в системе отопления) = " + heatAmountCO)
                .add("  heatAmountVENT (Количество теплоты в системе вентиляции) = " + heatAmountVENT)
                .add("  heatAmountAccumulatedGVS (Накопленное количество теплоты в системе ГВС) = " + heatAmountAccumulatedGVS)
                .add("  heatAmountAccumulatedCO (Накопленное количество теплоты в системе отопления) = " + heatAmountAccumulatedCO)
                .add("  heatAmountAccumulatedVENT (Накопленное количество теплоты в системе вентиляции) = " + heatAmountAccumulatedVENT)
                .add("  offTime (Время выключенного состояния) = " + offTime)
                .add("  offTimeAccumulated (Накопленное время выключеннного состояния) = " + offTimeAccumulated)
                .add("  stopTimeGError1 (Время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов)) = " + Arrays.toString(stopTimeGError1))
                .add("  stopTimeGError2 (Время останова измерений G (превышение уставки Gmin)) = " + Arrays.toString(stopTimeGError2))
                .add("  stopTimeGError3 (Время останова измерений G (превышение уставки Gmax)) = " + Arrays.toString(stopTimeGError3))
                .add("  workingTimeG (Время наработки G) = " + Arrays.toString(workingTimeG))
                .add("  stopTimeT (Время останова T) = " + Arrays.toString(stopTimeT))
                .add("  workingTimeT (Время наработки T) = " + Arrays.toString(workingTimeT))
                .add("  stopTimeP (Время останова P) = " + Arrays.toString(stopTimeP))
                .add("  workingTimeP (Время наработки P) = " + Arrays.toString(workingTimeP))
                .add("  stopTimeMQ (Время останова M/Q (при недостоверности G, T, P)) = " + Arrays.toString(stopTimeMQ))
                .add("  workingTimeMQ (Время наработки M/Q) = " + Arrays.toString(workingTimeMQ))
                .add("  accumulatedStopTimeGError1 (Накопленное время останова измерений G (отказ ПЭП, отсечка G, несоответствие скорости звука, коррекция часов)) = " + Arrays.toString(accumulatedStopTimeGError1))
                .add("  accumulatedStopTimeGError2 (Накопленное время останова измерений G (превышение уставки Gmin)) = " + Arrays.toString(accumulatedStopTimeGError2))
                .add("  accumulatedStopTimeGError3 (Накопленное время останова измерений G (превышение уставки Gmax)) = " + Arrays.toString(accumulatedStopTimeGError3))
                .add("  accumulatedWorkingTimeG (Накопленное время наработки G) = " + Arrays.toString(accumulatedWorkingTimeG))
                .add("  accumulatedStopTimeMQ (Накопленное время останова M/Q (при недостоверности G, T, P)) = " + Arrays.toString(accumulatedStopTimeMQ))
                .add("  accumulatedWorkingTimeMQ (Накопленное время наработки M/Q) = " + Arrays.toString(accumulatedWorkingTimeMQ))
                .add("  currentStopTimeError1 (Время останова измерений при перепаде температур П-О <= 3) = " + Arrays.toString(currentStopTimeError1))
                .add("  currentStopTimeError2 (Время останова измерений с прочими отказами (отказ одного из каналов)) = " + Arrays.toString(currentStopTimeError2))
                .add("  workingTimeQ (Время наработки Q) = " + Arrays.toString(workingTimeQ))
                .add("  waterHeatZone (Количество тепла) = " + Arrays.toString(waterHeatZone))
                .add("  accumulatedCurrentStopTimeError1 (Накопленное время останова измерений при перепаде температур П-О <= 3) = " + Arrays.toString(accumulatedCurrentStopTimeError1))
                .add("  accumulatedCurrentStopTimeError2 (Накопленное время останова измерений с прочими отказами (отказ одного из каналов)) = " + Arrays.toString(accumulatedCurrentStopTimeError2))
                .add("  accumulatedWorkingTimeQ (Накопленное время наработки Q) = " + Arrays.toString(accumulatedWorkingTimeQ))
                .add("  accumulatedWaterHeatZone (Накопленное количество тепла) = " + Arrays.toString(accumulatedWaterHeatZone))
                .toString();
    }
}
