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

package stroom.pipeline.state;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class RecordCountService {

    private final AtomicLong readCount = new AtomicLong();
    private final AtomicLong writeCount = new AtomicLong();

    public Incrementor getReadIncrementor() {
        return readCount::incrementAndGet;
    }

    public Incrementor getWriteIncrementor() {
        return writeCount::incrementAndGet;
    }

    public long getAndResetRead() {
        final long count = readCount.get();
        readCount.addAndGet(-count);
        return count;
    }

    public long getAndResetWritten() {
        final long count = writeCount.get();
        writeCount.addAndGet(-count);
        return count;
    }
}
