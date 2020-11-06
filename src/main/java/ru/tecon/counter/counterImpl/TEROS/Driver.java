package ru.tecon.counter.counterImpl.TEROS;

import ru.tecon.counter.Counter;
import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;
import ru.tecon.counter.exception.DriverDataLoadException;
import ru.tecon.counter.util.Drivers;
import ru.tecon.counter.util.FileData;
import ru.tecon.counter.util.ServerNames;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Driver extends Counter {

    private static Logger log = Logger.getLogger(Driver.class.getName());

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");

    private static final List<String> PATTERNS = Collections.singletonList("\\d{4}t\\d{8}-\\d{2}");

    private Float pti;
    private Double ptd;
    private Float time0;
    private Float time1;
    private Float time2;
    private Float time3;
    private Float time4;
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
    private Double v1d;
    private Double v2d;
    private Double vpd;
    private Float g1i;
    private Float g2i;
    private Float gpi;
    private Double g1d;
    private Double g2d;
    private Double gpd;

    private int quality = 192;

    private String url;

    private static final Map<String, String> methodsMap = new HashMap<>();

    static {
        Method[] method = Driver.class.getMethods();

        for(Method md: method){
            if (md.isAnnotationPresent(TEROSCounterParameter.class)) {
                methodsMap.put(md.getAnnotation(TEROSCounterParameter.class).name().getProperty(), md.getName());
            }
        }
    }

    public Driver() {
        try {
            Context ctx = new InitialContext();
            url = (String) ctx.lookup("java:comp/env/url");
        } catch (NamingException e) {
            log.warning("error load context");
        }
    }

    @Override
    public List<String> getObjects() {
        List<String> objects = Drivers.scan(url, PATTERNS);
        return objects.stream().map(s -> ServerNames.MCT_20_TEROS + "-" + s).collect(Collectors.toList());
    }

    @Override
    public List<String> getConfig(String object) {
        return Stream.of(TEROSConfig.values())
                .map(TEROSConfig::getProperty)
                .collect(Collectors.toList());
    }

    @Override
    public void loadData(List<DataModel> params, String objectName) {
        log.info("loadData start " + objectName);

        Collections.sort(params);

        String counterNumber = objectName.substring(objectName.length() - 4);
        String filePath = url + "/" + counterNumber.substring(0, 2) + "/" + counterNumber + "/";

        LocalDateTime date = params.get(0).getStartTime() == null ? null : params.get(0).getStartTime().minusHours(1);

        Class<?> cl = this.getClass();

        List<FileData> fileData = Drivers.getFilesForLoad(filePath, date, PATTERNS);

        for (FileData fData: fileData) {
            try {
                if (Files.exists(fData.getPath())) {
                    readFile(fData.getPath());

                    for (DataModel model: params) {
                        if (model.getStartTime() == null || fData.getDateTime().isAfter(model.getStartTime().minusHours(1))) {
                            try {
                                String mName = methodsMap.get(model.getParamName());
                                if (Objects.nonNull(mName)) {
                                    Object value = cl.getMethod(mName).invoke(this);
                                    if (value == null) {
                                        continue;
                                    }
                                    if (value instanceof Double) {
                                        model.addData(new ValueModel(Double.toString((Double) value), fData.getDateTime(), quality));
                                    } else {
                                        if (value instanceof Float) {
                                            model.addData(new ValueModel(Float.toString((Float) value), fData.getDateTime(), quality));
                                        } else {
                                            if (value instanceof String) {
                                                model.addData(new ValueModel((String) value, fData.getDateTime(), quality));
                                            }
                                        }
                                    }
                                }
                            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                                log.warning("error invoke method");
                            }
                        }
                    }
                }
            } catch (DriverDataLoadException e) {
                log.log(Level.WARNING, objectName + " " + fData.getPath(), e);
            } catch (IOException ex) {
                log.warning("loadData end error " + objectName);
                return;
            }
        }

        log.info("loadData end " + objectName);
    }

    @Override
    public void clearHistorical() {
        Drivers.clear(this, url, PATTERNS);
    }

    private void readFile(Path path) throws DriverDataLoadException, IOException {
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ))) {
            if (inputStream.available() != 537) {
                throw new DriverDataLoadException("error size of file");
            }

            byte[] buffer = new byte[inputStream.available()];
            if (inputStream.read(buffer) != 537) {
                throw new DriverDataLoadException("error read file");
            }

            if (buffer[buffer.length - 1] != 10) {
                throw new DriverDataLoadException("Ошибочное окончание записи");
            }

            if ((((buffer[18] & 0x0f) >> 3) != 1) || (((buffer[18] & 0x10) >> 4) != 1)) {
                throw new DriverDataLoadException("Ошибка в valid");
            }

            if ((buffer[19] & 0x0f) != 0) {
                throw new DriverDataLoadException("В файле присутствуют ошибки");
            }

            if (Drivers.computeCrc16(Arrays.copyOfRange(buffer, 2, buffer.length - 1)) !=
                    Short.toUnsignedInt(ByteBuffer.wrap(buffer, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort())) {
                throw new DriverDataLoadException("Ошибка в crc16");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            timeUspd = createDate(Arrays.copyOfRange(buffer, 2, 8));
            timeTs = LocalDateTime.parse(createDate(Arrays.copyOfRange(buffer, 8, 14)), FORMATTER)
                    .plusHours(3)
                    .format(FORMATTER);

            t1 = byteBuffer.getFloat(48);
            t2 = byteBuffer.getFloat(52);
            tp = byteBuffer.getFloat(56);
            p1 = byteBuffer.getFloat(60);
            p2 = byteBuffer.getFloat(64);
            pt = byteBuffer.getFloat(68);

            g1i = byteBuffer.getFloat(292);
            g2i = byteBuffer.getFloat(296);
            gpi = byteBuffer.getFloat(300);

            v1i = byteBuffer.getFloat(304);
            v2i = byteBuffer.getFloat(308);
            vpi = byteBuffer.getFloat(317);

            pti = byteBuffer.getFloat(313);

            time0 = byteBuffer.getFloat(321);
            time1 = byteBuffer.getFloat(329);
            time2 = byteBuffer.getFloat(337);
            time3 = byteBuffer.getFloat(333);
            time4 = byteBuffer.getFloat(325);

        } catch (DateTimeParseException e) {
            throw new DriverDataLoadException("parse data Exception");
        }
    }

    private String createDate(byte[] buffer) {
        return createStringValue(buffer[2]) +
                createStringValue(buffer[1]) +
                ((buffer[0] & 0xff) < 95 ? ("20" + createStringValue(buffer[0])) : ("19" + createStringValue(buffer[0]))) +
                createStringValue(buffer[3]) +
                createStringValue(buffer[4]) +
                createStringValue(buffer[5]);
    }

    private String createStringValue(byte b) {
        return (b & 0xff) < 10 ? ("0" + (b & 0xff)) : String.valueOf(b & 0xff);
    }

    public void printData(String path) {
        try {
            this.readFile(Paths.get(path));
            System.out.println(this);
        } catch (DriverDataLoadException | IOException e) {
            log.log(Level.WARNING, "printData error", e);
        }
    }

    @TEROSCounterParameter(name = TEROSConfig.PTI)
    public Float getPti() {
        return pti;
    }

    @TEROSCounterParameter(name = TEROSConfig.PTD)
    public Double getPtd() {
        return ptd;
    }

    @TEROSCounterParameter(name = TEROSConfig.TIME_0)
    public Float getTime0() {
        return time0;
    }

    @TEROSCounterParameter(name = TEROSConfig.TIME_1)
    public Float getTime1() {
        return time1;
    }

    @TEROSCounterParameter(name = TEROSConfig.TIME_2)
    public Float getTime2() {
        return time2;
    }

    @TEROSCounterParameter(name = TEROSConfig.TIME_3)
    public Float getTime3() {
        return time3;
    }

    @TEROSCounterParameter(name = TEROSConfig.TIME_4)
    public Float getTime4() {
        return time4;
    }

    @TEROSCounterParameter(name = TEROSConfig.T1)
    public Float getT1() {
        return t1;
    }

    @TEROSCounterParameter(name = TEROSConfig.T2)
    public Float getT2() {
        return t2;
    }

    @TEROSCounterParameter(name = TEROSConfig.TP)
    public Float getTp() {
        return tp;
    }

    @TEROSCounterParameter(name = TEROSConfig.P1)
    public Float getP1() {
        return p1;
    }

    @TEROSCounterParameter(name = TEROSConfig.P2)
    public Float getP2() {
        return p2;
    }

    @TEROSCounterParameter(name = TEROSConfig.PT)
    public Float getPt() {
        return pt;
    }

    @TEROSCounterParameter(name = TEROSConfig.TIME_TS)
    public String getTimeTs() {
        return timeTs;
    }

    @TEROSCounterParameter(name = TEROSConfig.TIME_USPD)
    public String getTimeUspd() {
        return timeUspd;
    }

    @TEROSCounterParameter(name = TEROSConfig.V1I)
    public Float getV1i() {
        return v1i;
    }

    @TEROSCounterParameter(name = TEROSConfig.V2I)
    public Float getV2i() {
        return v2i;
    }

    @TEROSCounterParameter(name = TEROSConfig.VPI)
    public Float getVpi() {
        return vpi;
    }

    @TEROSCounterParameter(name = TEROSConfig.V1D)
    public Double getV1d() {
        return v1d;
    }

    @TEROSCounterParameter(name = TEROSConfig.V2D)
    public Double getV2d() {
        return v2d;
    }

    @TEROSCounterParameter(name = TEROSConfig.VPD)
    public Double getVpd() {
        return vpd;
    }

    @TEROSCounterParameter(name = TEROSConfig.G1I)
    public Float getG1i() {
        return g1i;
    }

    @TEROSCounterParameter(name = TEROSConfig.G2I)
    public Float getG2i() {
        return g2i;
    }

    @TEROSCounterParameter(name = TEROSConfig.GPI)
    public Float getGpi() {
        return gpi;
    }

    @TEROSCounterParameter(name = TEROSConfig.G1D)
    public Double getG1d() {
        return g1d;
    }

    @TEROSCounterParameter(name = TEROSConfig.G2D)
    public Double getG2d() {
        return g2d;
    }

    @TEROSCounterParameter(name = TEROSConfig.GPD)
    public Double getGpd() {
        return gpd;
    }

    @Override
    public String toString() {
        return new StringJoiner(",\n", Driver.class.getSimpleName() + "[\n", "\n]")
                .add("  Качаство (quality): " + quality)
                .add("  Количество теплоты нарастающим итогом (pti): " + pti)
                .add("  Количество теплоты за время (ptd): " + ptd)
                .add("  Нарастающее время в состоянии 0 (time0): " + time0)
                .add("  Нарастающее время в состоянии 1 (time1): " + time1)
                .add("  Нарастающее время в состоянии 2 (time2): " + time2)
                .add("  Нарастающее время в состоянии 3 (time3): " + time3)
                .add("  Нарастающее время в состоянии 4 (time4): " + time4)
                .add("  Температура в подающем трубопроводе теплосети (t1): " + t1)
                .add("  Температура в обратном трубопроводе теплосети (t2): " + t2)
                .add("  Температура в подпиточном трубопроводе теплосети (tp): " + tp)
                .add("  Давление в подающем трубопроводе теплосети (p1): " + p1)
                .add("  Давление в обратном трубопроводе теплосети (p2): " + p2)
                .add("  Давление в подпиточном трубопроводе теплосети (pt): " + pt)
                .add("  Время ТС (timeTs): '" + timeTs + "'")
                .add("  Время УСПД (timeUspd): '" + timeUspd + "'")
                .add("  Объемный расход в подающем трубопроводе нарастающим итогом (v1i): " + v1i)
                .add("  Объемный расход в обратном трубопроводе нарастающим итогом (v2i): " + v2i)
                .add("  Объемный расход в подпиточном трубопроводе нарастающим итогом (vpi): " + vpi)
                .add("  Объемный расход в подающем трубопроводе за время (v1d): " + v1d)
                .add("  Объемный расход в обратном трубопроводе за время (v2d): " + v2d)
                .add("  Объемный расход в подпиточном трубопроводе за время (vpd): " + vpd)
                .add("  Массовый расход в подающем трубопроводе нарастающим итогом (g1i): " + g1i)
                .add("  Массовый расход в обратном трубопроводе нарастающим итогом (g2i): " + g2i)
                .add("  Массовый расход в подпиточном трубопроводе нарастающим итогом (gpi): " + gpi)
                .add("  Массовый расход в подающем трубопроводе за время (g1d): " + g1d)
                .add("  Массовый расход в обратном трубопроводе за время (g2d): " + g2d)
                .add("  Массовый расход в подпиточном трубопроводе за время (gpd): " + gpd)
                .toString();
    }
}
