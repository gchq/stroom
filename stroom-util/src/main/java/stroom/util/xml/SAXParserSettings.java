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

package stroom.util.xml;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class SAXParserSettings {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SAXParserSettings.class);

    private static final AtomicBoolean SECURE_PROCESSING = new AtomicBoolean(true);
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);

    public static boolean isSecureProcessingEnabled() {
//        try {
//            // Need to ensure nothing gets the value until it has been set to something.
//            LOGGER.debug("Waiting for isSecureProcessingEnabled to be set");
//            final boolean result = countDownLatch.await(10, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("Interrupted waiting for isSecureProcessingEnabled to be set");
//        }
        return SECURE_PROCESSING.get();
    }

    public static void setSecureProcessingEnabled(final boolean isEnabled) {
//        if (countDownLatch.getCount() > 0) {
        SECURE_PROCESSING.set(isEnabled);
//            countDownLatch.countDown();
//        }
    }
}
