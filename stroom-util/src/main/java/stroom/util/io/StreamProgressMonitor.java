/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Monitor;

import java.io.IOException;

public class StreamProgressMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamProgressMonitor.class);

    private final Monitor monitor;
    private final String prefix;
    private final long INTERVAL_MS = 1000;
    private long totalBytes = 0;
    private long lastProgressTime = System.currentTimeMillis();

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
