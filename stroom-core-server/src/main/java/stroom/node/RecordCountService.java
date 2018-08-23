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

package stroom.node;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class RecordCountService {
    private final Set<RecordCounter> recordReadCounters = new HashSet<>();
    private final Set<RecordCounter> recordWriteCounters = new HashSet<>();
    private long recordsRead;
    private long recordsWritten;

    public synchronized void addRecordReadCounter(final RecordCounter counter) {
        recordReadCounters.add(counter);
    }

    public synchronized void removeRecordReadCounter(final RecordCounter counter) {
        recordReadCounters.remove(counter);
        recordsRead += counter.getAndResetCount();
    }

    public synchronized void addRecordWrittenCounter(final RecordCounter counter) {
        recordWriteCounters.add(counter);
    }

    public synchronized void removeRecordWrittenCounter(final RecordCounter counter) {
        recordWriteCounters.remove(counter);
        recordsWritten += counter.getAndResetCount();
    }

    synchronized long getAndResetRead() {
        long count = recordsRead;
        recordsRead = 0;
        for (final RecordCounter counter : recordReadCounters) {
            count += counter.getAndResetCount();
        }
        return count;
    }

    synchronized long getAndResetWritten() {
        long count = recordsWritten;
        recordsWritten = 0;
        for (final RecordCounter counter : recordWriteCounters) {
            count += counter.getAndResetCount();
        }
        return count;
    }
}
