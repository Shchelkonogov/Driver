package ru.tecon.alarm;

import ru.tecon.counter.model.DataModel;
import ru.tecon.counter.model.ValueModel;
import ru.tecon.counter.util.Drivers;
import ru.tecon.counter.util.FileData;

import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

public class Parse {

    private static Logger log = Logger.getLogger(Parse.class.getName());

    public static void parse(List<DataModel> params, String alarmPath, String objectName) {
        String counterNumber = objectName.substring(objectName.length() - 4);

        Collections.sort(params);

        List<FileData> fileData = Drivers.getFilesForLoad(alarmPath, params.get(0).getStartTime(),
                Collections.singletonList(counterNumber + "a\\d{8}-\\d{6}"), "yyyyMMdd-HHmmss");

        for (FileData fData: fileData) {
            try {
                if (Files.exists(fData.getPath())) {

                    String value = null;
                    List<String> lines = Files.readAllLines(fData.getPath());
                    if ((lines.size() > 0) && (lines.get(0).contains("="))) {
                        value = lines.get(0).substring(lines.get(0).indexOf("=") + 1, lines.get(0).indexOf("=") + 2);
                    }

                    for (DataModel model: params) {
                        if (model.getStartTime() == null || fData.getDateTime().isAfter(model.getStartTime())) {
                            model.addData(new ValueModel(value, fData.getDateTime().minusHours(1), 192));
                        }
                    }
                }
            } catch (Exception e) {
                log.warning(objectName + " " + fData.getPath() + " " + e.getMessage());
                if (e.getMessage().equals("IOException")) {
                    return;
                }
            }
        }
    }
}
