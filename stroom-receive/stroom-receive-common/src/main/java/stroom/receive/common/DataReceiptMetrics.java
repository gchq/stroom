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

package stroom.receive.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DataReceiptMetrics {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataReceiptMetrics.class);

    private final Timer requestTimer;
    private final Histogram contentLengthHistogram;

    @Inject
    public DataReceiptMetrics(final Metrics metrics) {
        this.requestTimer = metrics.registrationBuilder(getClass())
                .addNamePart("request")
                .addNamePart("time")
                .timer()
                .createAndRegister();
        this.contentLengthHistogram = metrics.registrationBuilder(getClass())
                .addNamePart("contentLength")
                .addNamePart(Metrics.SIZE_IN_BYTES)
                .histogram()
                .createAndRegister();
    }

    public void timeRequest(final Runnable runnable) {
        requestTimer.time(runnable);
    }

    public Timer getRequestTimer() {
        return requestTimer;
    }

    public void recordContentLength(final String contentLength) {
        if (NullSafe.isNonEmptyString(contentLength)) {
            try {
                final long len = Long.parseLong(contentLength);
                contentLengthHistogram.update(len);
            } catch (final NumberFormatException e) {
                LOGGER.debug("Unable to parse '{}' to a long", contentLength);
            }
        }
    }
}
