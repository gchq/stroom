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

import java.io.IOException;

import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Monitor;

public class StreamProgressMonitor {
    private static StroomLogger LOGGER = StroomLogger.getLogger(StreamProgressMonitor.class);

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
            String msg = prefix + " - " + ModelStringUtil.formatByteSizeString(totalBytes);
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
