package stroom.proxy.handler;

import stroom.proxy.util.ProxyProperties;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LogRequestConfig {
    private final List<String> logRequest;

    @Inject
    public LogRequestConfig(@Named(ProxyProperties.LOG_REQUEST) final String logRequestCsv) {
        if (logRequestCsv != null && logRequestCsv.length() > 0) {
            logRequest = Arrays.stream(logRequestCsv.split(",")).collect(Collectors.toList());
        } else {
            logRequest = Collections.emptyList();
        }
    }

    public List<String> getLogRequestList() {
        return logRequest;
    }
}
