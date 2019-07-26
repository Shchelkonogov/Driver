package ru.tecon.counter.SA94;

import ru.tecon.counter.Counter;
import ru.tecon.counter.util.DriverLoadException;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;
import ru.tecon.counter.util.Drivers;
import ru.tecon.counter.util.FileData;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Driver implements Counter {

    private static Logger log = Logger.getLogger(Driver.class.getName());

    private static final List<String> PATTERN = Collections.singletonList("\\d{4}s\\d{8}-\\d{2}");

    private Float pti;
    private Float ptd;
    private Long time0;
    private Long time1;
    private Long time2;
    private Long time3;
    private Long time4;
    private Float t1;
    private Float t2;
    private Float tp;
    private Float p1;
    private Float p2;
    private Float pt;
    private String timeTs;
    private String timeUspd;
    private Float v1i;
    private Float v2i;
    private Float vpi;
    private Float v1d;
    private Float v2d;
    private Float vpd;
    private Float g1i;
    private Float g2i;
    private Float gpi;
    private Float g1d;
    private Float g2d;
    private Float gpd;

    private String url;

    public Driver() {
        try {
            Context ctx = new InitialContext();
            url = (String) ctx.lookup("java:comp/env/url");
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getObjects() {
        List<String> objects = Drivers.scan(url, PATTERN);
        return objects.stream().map(e -> e = "МСТ-20-SA94-" + e).collect(Collectors.toList());
    }

    @Override
    public List<String> getConfig(String object) {
        return Stream.of(SA94Config.values())
                .map(SA94Config::getProperty)
                .collect(Collectors.toList());
    }

    @Override
    public void clear() {
        Drivers.clear(this, url, PATTERN);
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        log.info("loadData start " + objectName);

        Collections.sort(params);

        String counterNumber = objectName.substring(objectName.length() - 4);
        String filePath = url + "/" + counterNumber.substring(0, 2) + "/" + counterNumber + "/";

        LocalDateTime date = params.get(0).getStartTime() == null ? null : params.get(0).getStartTime().plusHours(2);

        Class<?> cl = this.getClass();
        Map<String, String> methodsMap = this.getMethodsMap();

        List<FileData> fileData = Drivers.getFilesForLoad(filePath, date, PATTERN);

        for (FileData fData: fileData) {
            try {
                if (Files.exists(fData.getPath())) {
                    readFile(fData.getPath().toString());

                    for (DataModel model: params) {
                        if (model.getStartTime() == null || fData.getDateTime().isAfter(model.getStartTime().plusHours(2))) {
                            try {
                                String mName = methodsMap.get(model.getParamName());
                                if (Objects.nonNull(mName)) {
                                    Object value = cl.getMethod(mName).invoke(this);
                                    if (value == null) {
                                        continue;
                                    }
                                    if (value instanceof Long) {
                                        model.addData(new ValueModel(Long.toString((Long) value), fData.getDateTime().minusHours(3)));
                                    } else {
                                        if (value instanceof Float) {
                                            model.addData(new ValueModel(Float.toString((Float) value), fData.getDateTime().minusHours(3)));
                                        } else {
                                            if (value instanceof String) {
                                                model.addData(new ValueModel((String) value, fData.getDateTime().minusHours(3)));
                                            }
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
                log.warning(objectName + " " + fData.getPath() + " " + e.getMessage());
                if (e.getMessage().equals("IOException")) {
                    log.warning("loadData end error " + objectName);
                    return;
                }
            }
        }

        log.info("loadData end " + objectName);
    }

    /**
     * Получение списка методов и их аннотаций в соответствии с конфигурацией
     * @return методы
     */
    private Map<String, String> getMethodsMap() {
        Map<String, String> result = new HashMap<>();
        Method[] method = this.getClass().getMethods();

        for(Method md: method){
            if (md.isAnnotationPresent(SA94CounterParameter.class)) {
                result.put(md.getAnnotation(SA94CounterParameter.class).name().getProperty(), md.getName());
            }
        }
        return result;
    }

    private void readFile(String path) throws DriverLoadException {
        log.info("Driver.readFile start read: " + path);
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(Paths.get(path),
                StandardOpenOption.READ))) {
            int bufferSize = 49;
            byte[] buffer = new byte[bufferSize];

            checkData(inputStream, buffer, bufferSize);

//            System.out.println("time in SA archive:");
//
//            System.out.println("day: " + (((buffer[3] & 0xff) >> 4) * 10 + (buffer[3] & 0x0f)));
//            System.out.println("mon: " + (((buffer[4] & 0xff) >> 4) * 10 + (buffer[4] & 0x0f)));
//            System.out.println("year: " + (((buffer[5] & 0xff) >> 4) * 10 + (buffer[5] & 0x0f)));
//
//            System.out.println("hour: " + (((buffer[7] & 0xff) >> 4) * 10 + (buffer[7] & 0x0f)));
//            System.out.println("min " + (((buffer[8] & 0xff) >> 4) * 10 + (buffer[8] & 0x0f)));
//            System.out.println("sec " + (((buffer[9] & 0xff) >> 4) * 10 + (buffer[9] & 0x0f)));

            v1i = readFloat(Arrays.copyOfRange(buffer, 10, 14));
            v2i = readFloat(Arrays.copyOfRange(buffer, 14, 18));
            t1 = new BigDecimal(String.valueOf(((buffer[18] & 0xff) << 8) | (buffer[19] & 0xff)))
                    .multiply(new BigDecimal("0.01")).floatValue();
            t2 = new BigDecimal(String.valueOf(((buffer[20] & 0xff) << 8) | (buffer[21] & 0xff)))
                    .multiply(new BigDecimal("0.01")).floatValue();
            pti = readFloat(Arrays.copyOfRange(buffer, 30, 34));

            timeTs = createDate(Arrays.copyOfRange(buffer, 34, 40));

            timeUspd = createDate(Arrays.copyOfRange(buffer, 40, 46));
        } catch (IOException e) {
            e.printStackTrace();
            throw new DriverLoadException("IOException");
        }
    }

    private void checkData(BufferedInputStream inputStream, byte[] buffer, int bufferSize)
            throws DriverLoadException, IOException {
        if (inputStream.available() < bufferSize) {
            log.warning("checkData Ошибка в данных");
            throw new DriverLoadException("checkData Ошибка в данных");
        }

        if (inputStream.read(buffer, 0, bufferSize) == -1) {
            log.warning("checkData Ошибка в чтение данных");
            throw new DriverLoadException("checkData Ошибка в чтение данных");
        }

//        if ((((buffer[1] & 0x04) >> 2) != 0) || ((buffer[1] >> 3) != 1)) {
        if ((buffer[1] >> 3) != 1) {
            log.warning("checkData Ошибка в valid");
            throw new DriverLoadException("checkData Ошибка в valid");
        }

        if ((buffer[bufferSize - 1] & 0xff) != 10) {
            log.warning("checkData Ошибочное окончание записи");
            throw new DriverLoadException("checkData Ошибочное окончание записи");
        }
    }

    private float readFloat(byte[] buffer) {
        if (((buffer[0] & 0xff) >> 1) == 0) {
            buffer[0] = 0x00;
        } else {
            buffer[0] -= 0x02;
        }

        return Float.intBitsToFloat(
                ((((buffer[0] & 0xff) >> 1) | (buffer[1] & 0x80)) << 24) |
                        (((buffer[1] & 0x7f)| ((buffer[0] & 0x01) << 7)) << 16) |
                        ((buffer[2] & 0xff) << 8) |
                        (buffer[3] & 0xff));
    }

    private String createDate(byte[] buffer) {
        return createStringValue(buffer[2]) +
                createStringValue(buffer[1]) +
                ((buffer[0] & 0xff) < 95 ? ("20" + (buffer[0] & 0xff)) : ("19" + (buffer[0] & 0xff))) +
                createStringValue(buffer[3]) +
                createStringValue(buffer[4]) +
                createStringValue(buffer[5]);
    }

    private String createStringValue(byte b) {
        return (b & 0xff) < 10 ? ("0" + (b & 0xff)) : String.valueOf(b & 0xff);
    }

    public void printData(String path) {
        try {
            this.readFile(path);
            System.out.println(this);
        } catch (DriverLoadException e) {
            log.warning(e.getMessage());
        }
    }

    @SA94CounterParameter(name = SA94Config.PTI)
    public Float getPti() {
        return pti;
    }

    @SA94CounterParameter(name = SA94Config.PTD)
    public Float getPtd() {
        return ptd;
    }

    @SA94CounterParameter(name = SA94Config.TIME_0)
    public Long getTime0() {
        return time0;
    }

    @SA94CounterParameter(name = SA94Config.TIME_1)
    public Long getTime1() {
        return time1;
    }

    @SA94CounterParameter(name = SA94Config.TIME_2)
    public Long getTime2() {
        return time2;
    }

    @SA94CounterParameter(name = SA94Config.TIME_3)
    public Long getTime3() {
        return time3;
    }

    @SA94CounterParameter(name = SA94Config.TIME_4)
    public Long getTime4() {
        return time4;
    }

    @SA94CounterParameter(name = SA94Config.T1)
    public Float getT1() {
        return t1;
    }

    @SA94CounterParameter(name = SA94Config.T2)
    public Float getT2() {
        return t2;
    }

    @SA94CounterParameter(name = SA94Config.TP)
    public Float getTp() {
        return tp;
    }

    @SA94CounterParameter(name = SA94Config.P1)
    public Float getP1() {
        return p1;
    }

    @SA94CounterParameter(name = SA94Config.P2)
    public Float getP2() {
        return p2;
    }

    @SA94CounterParameter(name = SA94Config.PT)
    public Float getPt() {
        return pt;
    }

    @SA94CounterParameter(name = SA94Config.TIME_TS)
    public String getTimeTs() {
        return timeTs;
    }

    @SA94CounterParameter(name = SA94Config.TIME_USPD)
    public String getTimeUspd() {
        return timeUspd;
    }

    @SA94CounterParameter(name = SA94Config.V1I)
    public Float getV1i() {
        return v1i;
    }

    @SA94CounterParameter(name = SA94Config.V2I)
    public Float getV2i() {
        return v2i;
    }

    @SA94CounterParameter(name = SA94Config.VPI)
    public Float getVpi() {
        return vpi;
    }

    @SA94CounterParameter(name = SA94Config.V1D)
    public Float getV1d() {
        return v1d;
    }

    @SA94CounterParameter(name = SA94Config.V2D)
    public Float getV2d() {
        return v2d;
    }

    @SA94CounterParameter(name = SA94Config.VPD)
    public Float getVpd() {
        return vpd;
    }

    @SA94CounterParameter(name = SA94Config.G1I)
    public Float getG1i() {
        return g1i;
    }

    @SA94CounterParameter(name = SA94Config.G2I)
    public Float getG2i() {
        return g2i;
    }

    @SA94CounterParameter(name = SA94Config.GPI)
    public Float getGpi() {
        return gpi;
    }

    @SA94CounterParameter(name = SA94Config.G1D)
    public Float getG1d() {
        return g1d;
    }

    @SA94CounterParameter(name = SA94Config.G2D)
    public Float getG2d() {
        return g2d;
    }

    @SA94CounterParameter(name = SA94Config.GPD)
    public Float getGpd() {
        return gpd;
    }

    @Override
    public String toString() {
        return new StringJoiner(",\n", Driver.class.getSimpleName() + "[\n", "\n]")
                .add("  Количество теплоты нарастающим итогом: " + pti)
                .add("  Количество теплоты за время: " + ptd)
                .add("  Нарастающее время в состоянии 0: " + time0)
                .add("  Нарастающее время в состоянии 1: " + time1)
                .add("  Нарастающее время в состоянии 2: " + time2)
                .add("  Нарастающее время в состоянии 3: " + time3)
                .add("  Нарастающее время в состоянии 4: " + time4)
                .add("  Температура в подающем трубопроводе теплосети: " + t1)
                .add("  Температура в обратном трубопроводе теплосети: " + t2)
                .add("  Температура в подпиточном трубопроводе теплосети: " + tp)
                .add("  Давление в подающем трубопроводе теплосети: " + p1)
                .add("  Давление в обратном трубопроводе теплосети: " + p2)
                .add("  Давление в подпиточном трубопроводе теплосети: " + pt)
                .add("  Время ТС: '" + timeTs + "'")
                .add("  Время УСПД: '" + timeUspd + "'")
                .add("  Объемный расход в подающем трубопроводе нарастающим итогом: " + v1i)
                .add("  Объемный расход в обратном трубопроводе нарастающим итогом: " + v2i)
                .add("  Объемный расход в подпиточном трубопроводе нарастающим итогом: " + vpi)
                .add("  Объемный расход в подающем трубопроводе за время: " + v1d)
                .add("  Объемный расход в обратном трубопроводе за время: " + v2d)
                .add("  Объемный расход в подпиточном трубопроводе за время: " + vpd)
                .add("  Массовый расход в подающем трубопроводе нарастающим итогом: " + g1i)
                .add("  Массовый расход в обратном трубопроводе нарастающим итогом: " + g2i)
                .add("  Массовый расход в подпиточном трубопроводе нарастающим итогом: " + gpi)
                .add("  Массовый расход в подающем трубопроводе за время: " + g1d)
                .add("  Массовый расход в обратном трубопроводе за время: " + g2d)
                .add("  Массовый расход в подпиточном трубопроводе за время: " + gpd)
                .toString();
    }
}
