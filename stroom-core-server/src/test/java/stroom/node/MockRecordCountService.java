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

import stroom.node.shared.RecordCountService;
import stroom.node.shared.RecordCounter;

public class MockRecordCountService implements RecordCountService {
    @Override
    public long getAndResetRead() {
        return 0;
    }

    @Override
    public long getAndResetWritten() {
        return 0;
    }

    @Override
    public void addRecordReadCounter(RecordCounter counter) {
    }

    @Override
    public void removeRecordReadCounter(RecordCounter counter) {
    }

    @Override
    public void addRecordWrittenCounter(RecordCounter counter) {
    }

    @Override
    public void removeRecordWrittenCounter(RecordCounter counter) {
    }
}
