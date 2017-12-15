package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Monitor;

import java.io.IOException;

public class StreamProgressMonitor {
    private static Logger LOGGER = LoggerFactory.getLogger(StreamProgressMonitor.class);

    private final Monitor monitor;
    private final String prefix;
    private long totalBytes = 0;
    private long lastProgressTime = System.currentTimeMillis();
    private final long INTERVAL_MS = 1000;

    public StreamProgressMonitor(final Monitor monitor, final String prefix) {
        this.monitor = monitor;
        this.prefix = prefix;
    }

    public StreamProgressMonitor(String prefix) {
        this.monitor = null;
        this.prefix = prefix;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void progress(int thisBytes) throws IOException {
        totalBytes += thisBytes;
        long timeNow = System.currentTimeMillis();

        if (lastProgressTime + INTERVAL_MS < timeNow) {
            lastProgressTime = timeNow;
            String msg = prefix + " - " + ModelStringUtil.formatIECByteSizeString(totalBytes);
            if (monitor != null) {
                monitor.info(msg);

                if (monitor.isTerminated()) {
                    throw new IOException("Progress Stopped");
                }
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(msg);
            }
        }
    }
}
