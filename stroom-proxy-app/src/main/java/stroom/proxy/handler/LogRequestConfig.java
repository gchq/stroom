package stroom.proxy.handler;

import java.util.ArrayList;
import java.util.List;

public class LogRequestConfig {
    List<String> logRequest;

    public void setLogRequest(String logRequestCsv) {
        if (logRequestCsv != null) {
            logRequest = new ArrayList<>();
            for (String val : logRequestCsv.split(",")) {
                logRequest.add(val);
            }
        }
    }

    public List<String> getLogRequestList() {
        return logRequest;
    }
}
