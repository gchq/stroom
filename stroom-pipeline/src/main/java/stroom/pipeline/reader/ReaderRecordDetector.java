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

package stroom.pipeline.reader;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.stepping.SteppingController;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

public class ReaderRecordDetector extends FilterReader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReaderRecordDetector.class);

    private static final int MAX_COUNT = 10000;
    private final char[] readBuffer = new char[1024];
    private final SteppingController controller;
    private long currentStepNo;
    private int readCount = 0;
    private int readBufferOffset = 0;
    private boolean newStream = true;
    private boolean newRecord;
    private int count;
    private boolean end;

    ReaderRecordDetector(final Reader reader, final SteppingController controller) {
        super(reader);
        this.controller = controller;
    }

    @Override
    public int read(final char[] buf, final int off, final int len) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new TaskTerminatedException();
        }

        // No stepping controller so
        if (controller == null) {
            return super.read(buf, off, len);
        }

        if (end) {
            return -1;
        }
        if (newStream) {
            currentStepNo = 0;
            controller.resetSourceLocation();

            newStream = false;
        }
        if (newRecord) {
            // Reset
            newRecord = false;
            count = 0;

            currentStepNo++;

            try {
                // Tell the controller that this is the end of a record.
                if (controller.endRecord(currentStepNo)) {
                    end = true;
                    return -1;
                }

                return 0;
            } catch (final ProcessException e) {
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                throw e;
            }
        }

        // On the previous read, we may have dropped out before consuming all the chars, i.e.
        // if we hit the end of a record, so only read if everything was consumed
        if (readBufferOffset == readCount) {
            // Fill the buffer.
            readCount = super.read(readBuffer, 0, Math.min(readBuffer.length, len));
            readBufferOffset = 0;
        }

        if (readCount == -1) {
            // The next time anybody tries to read from this reader it will be a
            // new stream, which will trigger a new source location to read from.
            newStream = true;
            return -1;
        }

        // Start from where we got to last time, or 0 if this is the first time consuming
        // chars after the read.
        int outputOffset = off;
        LOGGER.trace(() -> LogUtil.message("readCount: {}, consumedCount: {}, remainingCount: {}",
                readCount, readBufferOffset, readCount - readBufferOffset));

        while (readBufferOffset < readCount) {
            final char chr = readBuffer[readBufferOffset];
            buf[outputOffset++] = chr;
            count++;
            readBufferOffset++;

            if (chr == '\n' || count >= MAX_COUNT) {
                // The next time anybody tries to read from this reader it will
                // be a new record.
                newRecord = true;
                break;
            }
        }

        // How many chars we actually added to buf
        return outputOffset - off;
    }
}
