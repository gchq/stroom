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

package stroom.util.collections;

import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.jupiter.api.Test;


import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the round robin list works correctly.
 */

class TestRoundRobinSet {
    private static final int N3 = 3;
    private static final int N4 = 4;

    /**
     * Tests that the round robin list works correctly.
     */
    @Test
    void test() {
        final RoundRobinSet<RoundRobinSetTestObject> rrList = new RoundRobinSet<>();
        rrList.add(new RoundRobinSetTestObject(1));
        rrList.add(new RoundRobinSetTestObject(2));
        rrList.add(new RoundRobinSetTestObject(N3));
        rrList.add(new RoundRobinSetTestObject(N4));

        assertThat(getString(rrList)).isEqualTo("123");
        assertThat(getString(rrList)).isEqualTo("234");
        assertThat(getString(rrList)).isEqualTo("341");
        assertThat(getString(rrList)).isEqualTo("412");
        assertThat(getString(rrList)).isEqualTo("123");
    }

    @Test
    void testSync() throws InterruptedException {
        final RoundRobinSet<RoundRobinSetTestObject> rrList = new RoundRobinSet<>();
        rrList.add(new RoundRobinSetTestObject(1));
        rrList.add(new RoundRobinSetTestObject(2));
        rrList.add(new RoundRobinSetTestObject(N3));
        rrList.add(new RoundRobinSetTestObject(N4));

        final MutableLong failureCount = new MutableLong(0);

        final ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        Thread.sleep(10);

                        try {
                            getString(rrList);
                        } catch (final RuntimeException e) {
                            System.err.println(e.getMessage());
                            failureCount.increment();
                        }
                    }
                } catch (final InterruptedException e) {
                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.MINUTES);

        assertThat(failureCount.longValue()).isEqualTo(0);
    }

    private String getString(final Collection<RoundRobinSetTestObject> c) {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        for (final RoundRobinSetTestObject value : c) {
            sb.append(value);
            i++;
            if (i == N3) {
                break;
            }
        }
        return sb.toString();
    }

    static class RoundRobinSetTestObject {
        private static final long serialVersionUID = -7648759863152854689L;
        int i;

        public RoundRobinSetTestObject(final int i) {
            this.i = i;
        }

        @Override
        public String toString() {
            return String.valueOf(i);
        }
    }
}
