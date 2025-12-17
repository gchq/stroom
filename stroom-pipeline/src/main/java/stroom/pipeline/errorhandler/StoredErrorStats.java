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

package stroom.pipeline.errorhandler;

import stroom.util.shared.StoredError;

public class StoredErrorStats {
    private final boolean fatal;

    private long totalCount;
    private long recordCount;
    private long currentCount;
    private StoredError currentError;
    private boolean newStream = true;
    private boolean newRecord = true;

    public StoredErrorStats(final boolean fatal) {
        this.fatal = fatal;
    }

    public void increment() {
        if (!fatal) {
            currentCount++;
        }

        if (newRecord) {
            newStream = false;
            newRecord = false;
            recordCount++;
        }

        totalCount++;
    }

    public long checkRecord() {
        newRecord = true;
        currentError = null;

        long count = currentCount;
        currentCount = 0;

        if (fatal && !newStream) {
            count = 1;
        }

        return count;
    }

    public StoredError getCurrentError() {
        return currentError;
    }

    public void setCurrentError(final StoredError currentError) {
        this.currentError = currentError;
    }

    public long getRecordCount() {
        return recordCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void reset() {
        this.newStream = true;
        this.newRecord = true;
        currentError = null;
        currentCount = 0;
    }
}
