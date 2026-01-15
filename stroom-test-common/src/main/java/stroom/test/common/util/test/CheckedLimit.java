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

package stroom.test.common.util.test;

import java.util.concurrent.atomic.AtomicInteger;

public class CheckedLimit {

    final int limit;
    private final AtomicInteger atomicInteger = new AtomicInteger(0);

    public CheckedLimit(final int limit) {
        this.limit = limit;
    }

    public int increment() {
        final int newValue = atomicInteger.getAndIncrement();
        if (newValue >= limit) {
            throw new RuntimeException("limited exceeded");
        }
        return newValue;
    }

    public int decrement() {
        final int newValue = atomicInteger.getAndDecrement();
        if (newValue <= 0) {
            final RuntimeException exception = new RuntimeException("limited went below 0 !!");
            exception.printStackTrace();
            throw exception;
        }
        return newValue;

    }

}
