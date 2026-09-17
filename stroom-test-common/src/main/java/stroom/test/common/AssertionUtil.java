/*
 * Copyright 2026 Crown Copyright
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

package stroom.test.common;


import stroom.util.concurrent.UncheckedInterruptedException;

import org.assertj.core.api.Assertions;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AssertionUtil {

    public static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private AssertionUtil() {
    }

    /**
     * Asserts that countDownLatch has been counted down to zero before the default timeout
     * of 10s is reached.
     *
     * @param countDownLatch The {@link CountDownLatch} to check.
     */
    public static void assertAwait(final CountDownLatch countDownLatch) {
        assertAwait(countDownLatch, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Asserts that countDownLatch has been counted down to zero before timeoutSeconds isreached.
     *
     * @param countDownLatch The {@link CountDownLatch} to check.
     * @param timeoutSeconds The timeout in seconds
     */
    public static void assertAwait(final CountDownLatch countDownLatch,
                                   final int timeoutSeconds) {
        Objects.requireNonNull(countDownLatch);
        try {
            Assertions.assertThat(countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS))
                    .withFailMessage(
                            "CountDownLatch did not count down to zero within %d seconds",
                            timeoutSeconds)
                    .isTrue();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }
}
