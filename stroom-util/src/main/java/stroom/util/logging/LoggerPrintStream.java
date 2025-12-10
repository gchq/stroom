/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.logging;

import stroom.util.io.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility to log messages to log4j from a print writer.
 */
public class LoggerPrintStream extends PrintStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerPrintStream.class);

    /**
     * This logger can also look out for lines being written in the log.
     */
    private Map<String, AtomicInteger> watchTerms;
    private final Logger logger;
    private final boolean debug;
    private final LoggerBuffer loggerBuffer;

    public LoggerPrintStream(final Logger logger, final boolean debug) throws UnsupportedEncodingException {
        this(logger, debug, new LoggerBuffer());
    }

    public LoggerPrintStream(final Logger logger) throws UnsupportedEncodingException {
        this(logger, true, new LoggerBuffer());
    }

    private LoggerPrintStream(final Logger logger,
                              final boolean debug,
                              final LoggerBuffer os) throws UnsupportedEncodingException {
        super(os, false, StreamUtil.DEFAULT_CHARSET_NAME);
        this.logger = logger;
        this.debug = debug;
        this.loggerBuffer = os;
        this.loggerBuffer.parent = this;
    }

    public static LoggerPrintStream create(final Logger logger, final boolean debug) {
        try {
            return new LoggerPrintStream(logger, debug);
        } catch (final UnsupportedEncodingException useEx) {
            throw new RuntimeException(useEx);
        }
    }

    public static LoggerPrintStream create(final Logger logger) {
        try {
            return new LoggerPrintStream(logger);
        } catch (final UnsupportedEncodingException useEx) {
            throw new RuntimeException(useEx);
        }
    }

    public void addWatchTerm(final String watchTerm) {
        if (watchTerms == null) {
            watchTerms = new ConcurrentHashMap<>();
        }
        watchTerms.put(watchTerm, new AtomicInteger(0));
    }

    public int getWatchTermCount(final String watchTerm) {
        if (watchTerms != null && watchTerms.containsKey(watchTerm)) {
            return watchTerms.get(watchTerm).get();
        }
        return 0;
    }

    // kill the return chars and write to log4j
    void doFlush() {
        if (logger != null && loggerBuffer != null) {
            if ((logger.isDebugEnabled() && debug) || logger.isInfoEnabled() && !debug) {
                String logLine = loggerBuffer.toString(StreamUtil.DEFAULT_CHARSET);
                if (logLine.endsWith("\n")) {
                    logLine = logLine.substring(0, logLine.length() - 1);
                }
                if (logLine.length() > 0) {
                    // Any watch Terms
                    if (watchTerms != null) {
                        for (final Entry<String, AtomicInteger> watchTerm : watchTerms.entrySet()) {
                            int startIndex = -1;
                            while ((startIndex = logLine.indexOf(watchTerm.getKey(), startIndex + 1)) != -1) {
                                watchTerm.getValue().incrementAndGet();
                            }
                        }
                    }
                    if (debug) {
                        logger.debug(logLine);
                    } else {
                        logger.info(logLine);
                    }
                }
            }
            loggerBuffer.reset();
        }
    }

    static class LoggerBuffer extends ByteArrayOutputStream {

        LoggerPrintStream parent;

        @Override
        public void flush() {
            try {
                super.flush();
            } catch (final IOException e) {
                LOGGER.error("Unable to flush stream", e);
            }
            if (parent != null) {
                parent.doFlush();
            }
        }

        @Override
        public void write(final byte[] b) throws IOException {
            super.write(b);
            flush();
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            super.write(b, off, len);
            flush();
        }

        @Override
        public synchronized void write(final int b) {
            super.write(b);
            flush();
        }
    }

}
