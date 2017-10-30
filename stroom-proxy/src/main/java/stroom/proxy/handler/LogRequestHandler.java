package stroom.proxy.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.proxy.repo.StroomZipEntry;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LogRequestHandler implements RequestHandler {
    private static Logger LOGGER = LoggerFactory.getLogger(LogRequestHandler.class);

    private final List<String> logConfigList;
    private MetaMap metaMap;

    @Inject
    public LogRequestHandler(final LogRequestConfig logRequestConfig) {
        if (logRequestConfig.getLogRequest() != null && logRequestConfig.getLogRequest().length() > 0) {
            logConfigList = Arrays.stream(logRequestConfig.getLogRequest().split(",")).collect(Collectors.toList());
        } else {
            logConfigList = Collections.emptyList();
        }
    }

    @Override
    public void setMetaMap(final MetaMap metaMap) {
        this.metaMap = metaMap;
    }

    @Override
    public void handleHeader() throws IOException {
        if (logConfigList != null) {
            StringBuilder builder = new StringBuilder();
            for (String logKey : logConfigList) {
                if (builder.length() == 0) {
                    builder.append("log() - ");
                } else {
                    builder.append(",");
                }
                builder.append(logKey);
                builder.append("=");
                builder.append(metaMap.get(logKey));
            }
            LOGGER.info(builder.toString());
        }
    }

    @Override
    public void handleEntryStart(StroomZipEntry stroomZipEntry) throws IOException {
        // NA for LogRequestHandler
    }

    @Override
    public void handleEntryEnd() throws IOException {
        // NA for LogRequestHandler
    }

    @Override
    public void handleEntryData(byte[] buffer, int off, int len) throws IOException {
        // NA for LogRequestHandler
    }

    @Override
    public void handleError() throws IOException {
        // NA for LogRequestHandler
    }

    @Override
    public void handleFooter() throws IOException {
        // NA for LogRequestHandler
    }

    @Override
    public void validate() {
    }
}
