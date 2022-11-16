package ru.tecon.counter.counterImpl.VIST;

import ru.tecon.counter.Counter;
import ru.tecon.counter.exception.DriverDataLoadException;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss");

    private static final List<String> PATTERNS = Collections.singletonList("\\d{4}v\\d{8}-\\d{2}");

    private static Logger log = Logger.getLogger(Driver.class.getName());

    private static final String SERVER_NAME = "МСТ-20-VIST";

    private Double pti;
    private Double ptd;
    private Double time0;
    private Double time1;
    private Double time2;
    private Double time3;
    private Double time4;
    private ValueModel t1;
    private ValueModel t2;
    private ValueModel tp;
    private ValueModel p1;
    private ValueModel p2;
    private ValueModel pt;
    private String timeTs;
    private String timeUspd;
    private Double v1i;
    private Double v2i;
    private Double vpi;
    private Double v1d;
    private Double v2d;
    private Double vpd;
    private Double g1i;
    private Double g2i;
    private Double gpi;
    private Double g1d;
    private Double g2d;
    private Double gpd;

    private int quality = 192;

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
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public List<String> getObjects() {
        List<String> objects = Drivers.scan(url, PATTERNS);
        return objects.stream().map(s -> SERVER_NAME + "-" + s).collect(Collectors.toList());
    }

    @Override
    public List<String> getConfig(String object) {
        return Stream.of(VISTConfig.values())
                .map(VISTConfig::getProperty)
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
        Map<String, String> methodsMap = this.getMethodsMap();

        List<FileData> fileData = Drivers.getFilesForLoad(filePath, date, PATTERNS);

        for (FileData fData: fileData) {
            try {
                if (Files.exists(fData.getPath())) {
                    readFile(fData.getPath().toString());

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
                                            if ((value instanceof ValueModel) && (((ValueModel) value).getValue() != null)) {
                                                model.addData(new ValueModel(((ValueModel) value).getValue(), fData.getDateTime(), ((ValueModel) value).getQuality()));
                                            } else {
                                                if (value instanceof String) {
                                                    model.addData(new ValueModel((String) value, fData.getDateTime(), quality));
                                                }
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
                log.warning(objectName + " " + fData.getPath() + " " + e.getMessage());
                try {
                    Drivers.markFileError(fData.getPath());
                } catch (IOException ex) {
                    log.warning("error rename file");
                }
            } catch (IOException ex) {
                log.warning("loadData end error " + objectName);
                return;
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
            if (md.isAnnotationPresent(VISTCounterParameter.class)) {
                result.put(md.getAnnotation(VISTCounterParameter.class).name().getProperty(), md.getName());
            }
        }
        return result;
    }

    @Override
    public void clearHistorical() {
        Drivers.clear(this, url, PATTERNS);
    }

    private void readFile(String path) throws IOException, DriverDataLoadException {
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(Paths.get(path),
                StandardOpenOption.READ))) {
            if (inputStream.available() < 120) {
                throw new DriverDataLoadException("error size of file");
            }

            byte[] buffer = new byte[inputStream.available()];
            if (inputStream.read(buffer) == -1) {
                throw new DriverDataLoadException("error read file");
            }

            if (buffer[buffer.length -1] != 10) {
                throw new DriverDataLoadException("Ошибочное окончание записи");
            }

            if ((((buffer[19] & 0x0f) >> 3) != 1) || (((buffer[19] & 0x10) >> 4) != 1)) {
                throw new DriverDataLoadException("Ошибка в valid");
            }

            if (Drivers.computeCrc16(Arrays.copyOfRange(buffer, 2, buffer.length -1)) !=
                    Short.toUnsignedInt(ByteBuffer.wrap(buffer, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort())) {
                throw new DriverDataLoadException("Ошибка в crc16");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

            // TODO Возможно надо проверить crc внутри архива виста

            timeUspd = createDate(Arrays.copyOfRange(buffer, 2, 8));
            timeTs = LocalDateTime.parse(createDate(Arrays.copyOfRange(buffer, 8, 14)), FORMATTER)
                    .plusHours(3)
                    .format(FORMATTER);

            int accuracyChanel1 = Byte.toUnsignedInt(byteBuffer.get(78));
            int accuracyChanel2 = Byte.toUnsignedInt(byteBuffer.get(79));
            int accuracyChanel3 = Byte.toUnsignedInt(byteBuffer.get(80));
            int accuracyChanelEnergy = Byte.toUnsignedInt(byteBuffer.get(81));

            v1i = new BigDecimal(String.valueOf(byteBuffer.getInt(42))).movePointLeft(accuracyChanel1).doubleValue();
            v2i = new BigDecimal(String.valueOf(byteBuffer.getInt(46))).movePointLeft(accuracyChanel2).doubleValue();
            vpi = new BigDecimal(String.valueOf(byteBuffer.getInt(50))).movePointLeft(accuracyChanel3).doubleValue();

            g1i = new BigDecimal(String.valueOf(byteBuffer.getInt(54))).movePointLeft(accuracyChanel1).doubleValue();
            g2i = new BigDecimal(String.valueOf(byteBuffer.getInt(58))).movePointLeft(accuracyChanel1).doubleValue();
            gpi = new BigDecimal(String.valueOf(byteBuffer.getInt(62))).movePointLeft(accuracyChanel1).doubleValue();

            time0 = new BigDecimal(String.valueOf(Integer.toUnsignedLong(byteBuffer.getInt(66)))).movePointLeft(2).doubleValue();

            pti = new BigDecimal(String.valueOf(byteBuffer.getLong(70))).movePointLeft(accuracyChanelEnergy).doubleValue();

            String parameterItems = new StringBuffer(Long.toBinaryString(Integer.toUnsignedLong(byteBuffer.getInt(26)))).reverse().toString();

            int increment = 0;
            for (int i = 0; i < parameterItems.length(); i++) {
                switch (i) {
                    case 0:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Время наработки [ч] т/с"
                            increment += 1;
                        }
                        break;
                    case 1:
                        if (parameterItems.charAt(i) == '1') {
                            v1d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel1).doubleValue();
                            increment += 4;
                        }
                        break;
                    case 2:
                        if (parameterItems.charAt(i) == '1') {
                            v2d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel2).doubleValue();
                            increment += 4;
                        }
                        break;
                    case 3:
                        if (parameterItems.charAt(i) == '1') {
                            vpd = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel3).doubleValue();
                            increment += 4;
                        }
                        break;
                    case 4:
                        if (parameterItems.charAt(i) == '1') {
                            g1d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel1).doubleValue();
                            increment += 4;
                        }
                        break;
                    case 5:
                        if (parameterItems.charAt(i) == '1') {
                            g2d = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel2).doubleValue();
                            increment += 4;
                        }
                        break;
                    case 6:
                        if (parameterItems.charAt(i) == '1') {
                            gpd = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanel3).doubleValue();
                            increment += 4;
                        }
                        break;
                    case 7:
                        if (parameterItems.charAt(i) == '1') {
                            float value = new BigDecimal(String.valueOf(byteBuffer.getShort(121 + increment))).movePointLeft(1).floatValue();
                            if (value == -1000) {
                                t1 = new ValueModel(Float.toString(value), null, 0);
                            } else {
                                t1 = new ValueModel(Float.toString(value), null, quality);
                            }
                            increment += 2;
                        }
                        break;
                    case 8:
                        if (parameterItems.charAt(i) == '1') {
                            float value = new BigDecimal(String.valueOf(byteBuffer.getShort(121 + increment))).movePointLeft(1).floatValue();
                            if (value == -1000) {
                                t2 = new ValueModel(Float.toString(value), null, 0);
                            } else {
                                t2 = new ValueModel(Float.toString(value), null, quality);
                            }
                            increment += 2;
                        }
                        break;
                    case 9:
                        if (parameterItems.charAt(i) == '1') {
                            float value = new BigDecimal(String.valueOf(byteBuffer.getShort(121 + increment))).movePointLeft(1).floatValue();
                            if (value == -1000) {
                                tp = new ValueModel(Float.toString(value), null, 0);
                            } else {
                                tp = new ValueModel(Float.toString(value), null, quality);
                            }
                            increment += 2;
                        }
                        break;
                    case 10:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Средняя температура №4 (окружающая) [ºC] т/с"
                            increment += 2;
                        }
                        break;
                    case 11:
                        if (parameterItems.charAt(i) == '1') {
                            float value = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(1).floatValue();
                            if (value == 0) {
                                p1 = new ValueModel(Float.toString(value), null, 0);
                            } else {
                                p1 = new ValueModel(Float.toString(value), null, quality);
                            }
                            increment += 1;
                        }
                        break;
                    case 12:
                        if (parameterItems.charAt(i) == '1') {
                            float value = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(1).floatValue();
                            if (value == 0) {
                                p2 = new ValueModel(Float.toString(value), null, 0);
                            } else {
                                p2 = new ValueModel(Float.toString(value), null, quality);
                            }
                            increment += 1;
                        }
                        break;
                    case 13:
                        if (parameterItems.charAt(i) == '1') {
                            float value = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(1).floatValue();
                            if (value == 0) {
                                pt = new ValueModel(Float.toString(value), null, 0);
                            } else {
                                pt = new ValueModel(Float.toString(value), null, quality);
                            }
                            increment += 1;
                        }
                        break;
                    case 14:
                        if (parameterItems.charAt(i) == '1') {
                            ptd = new BigDecimal(String.valueOf(byteBuffer.getInt(121 + increment))).movePointLeft(accuracyChanelEnergy).doubleValue();
                            increment += 4;
                        }
                        break;
                    case 15:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Ошибки т/с"
                            increment += 4;
                        }
                        break;
                    case 16:
                        if (parameterItems.charAt(i) == '1') {
                            time1 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            increment += 1;
                        }
                        break;
                    case 17:
                        if (parameterItems.charAt(i) == '1') {
                            time2 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            increment += 1;
                        }
                        break;
                    case 18:
                        if (parameterItems.charAt(i) == '1') {
                            time3 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            increment += 1;
                        }
                        break;
                    case 19:
                        if (parameterItems.charAt(i) == '1') {
                            time4 = new BigDecimal(String.valueOf(Byte.toUnsignedInt(byteBuffer.get(121 + increment)))).movePointLeft(2).doubleValue();
                            increment += 1;
                        }
                        break;
                    case 20:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Зарезервировано"
                            increment += 3;
                        }
                        break;
                    case 21:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Общее учтенное время [ч] т/с (разделенные системы)"
                            increment += 1;
                        }
                        break;
                    case 22:
                        if (parameterItems.charAt(i) == '1') {
                            // Не используем "Время простоя [ч] т/с (датчик пустой трубы)"
                            increment += 1;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (DateTimeParseException e) {
            throw new DriverDataLoadException("parse data Exception", e);
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
            this.readFile(path);
            System.out.println(this);
        } catch (DriverDataLoadException | IOException e) {
            log.log(Level.WARNING, "printData error", e);
        }
    }

    @VISTCounterParameter(name = VISTConfig.PTI)
    public Double getPti() {
        return pti;
    }

    @VISTCounterParameter(name = VISTConfig.PTD)
    public Double getPtd() {
        return ptd;
    }

    @VISTCounterParameter(name = VISTConfig.TIME_0)
    public Double getTime0() {
        return time0;
    }

    @VISTCounterParameter(name = VISTConfig.TIME_1)
    public Double getTime1() {
        return time1;
    }

    @VISTCounterParameter(name = VISTConfig.TIME_2)
    public Double getTime2() {
        return time2;
    }

    @VISTCounterParameter(name = VISTConfig.TIME_3)
    public Double getTime3() {
        return time3;
    }

    @VISTCounterParameter(name = VISTConfig.TIME_4)
    public Double getTime4() {
        return time4;
    }

    @VISTCounterParameter(name = VISTConfig.T1)
    public ValueModel getT1() {
        return t1;
    }

    @VISTCounterParameter(name = VISTConfig.T2)
    public ValueModel getT2() {
        return t2;
    }

    @VISTCounterParameter(name = VISTConfig.TP)
    public ValueModel getTp() {
        return tp;
    }

    @VISTCounterParameter(name = VISTConfig.P1)
    public ValueModel getP1() {
        return p1;
    }

    @VISTCounterParameter(name = VISTConfig.P2)
    public ValueModel getP2() {
        return p2;
    }

    @VISTCounterParameter(name = VISTConfig.PT)
    public ValueModel getPt() {
        return pt;
    }

    @VISTCounterParameter(name = VISTConfig.TIME_TS)
    public String getTimeTs() {
        return timeTs;
    }

    @VISTCounterParameter(name = VISTConfig.TIME_USPD)
    public String getTimeUspd() {
        return timeUspd;
    }

    @VISTCounterParameter(name = VISTConfig.V1I)
    public Double getV1i() {
        return v1i;
    }

    @VISTCounterParameter(name = VISTConfig.V2I)
    public Double getV2i() {
        return v2i;
    }

    @VISTCounterParameter(name = VISTConfig.VPI)
    public Double getVpi() {
        return vpi;
    }

    @VISTCounterParameter(name = VISTConfig.V1D)
    public Double getV1d() {
        return v1d;
    }

    @VISTCounterParameter(name = VISTConfig.V2D)
    public Double getV2d() {
        return v2d;
    }

    @VISTCounterParameter(name = VISTConfig.VPD)
    public Double getVpd() {
        return vpd;
    }

    @VISTCounterParameter(name = VISTConfig.G1I)
    public Double getG1i() {
        return g1i;
    }

    @VISTCounterParameter(name = VISTConfig.G2I)
    public Double getG2i() {
        return g2i;
    }

    @VISTCounterParameter(name = VISTConfig.GPI)
    public Double getGpi() {
        return gpi;
    }

    @VISTCounterParameter(name = VISTConfig.G1D)
    public Double getG1d() {
        return g1d;
    }

    @VISTCounterParameter(name = VISTConfig.G2D)
    public Double getG2d() {
        return g2d;
    }

    @VISTCounterParameter(name = VISTConfig.GPD)
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
