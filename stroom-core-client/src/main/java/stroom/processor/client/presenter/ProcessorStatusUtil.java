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

package stroom.processor.client.presenter;

import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorListRow;
import stroom.util.shared.NullSafe;
import stroom.widget.customdatebox.client.MomentJs;

class ProcessorStatusUtil {

    public static String getValue(final ProcessorListRow row) {
        String status = null;
        if (row instanceof ProcessorFilterRow) {
            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
            final ProcessorFilterTracker tracker = processorFilterRow
                    .getProcessorFilter()
                    .getProcessorFilterTracker();
            if (tracker != null) {
                if (!NullSafe.isBlankString(tracker.getMessage())) {
                    status = NullSafe.getOrElseGet(
                            tracker.getStatus(),
                            status2 -> status2.getDisplayValue() + ": " + tracker.getMessage(),
                            tracker::getMessage);
                } else if (!ProcessorFilterTrackerStatus.CREATED.equals(tracker.getStatus())) {
                    status = tracker.getStatus().getDisplayValue();
                } else if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() == 0) {
                    status = "Up to date";
                } else if (tracker.getMetaCreateMs() != null) {
                    final long age = System.currentTimeMillis() - tracker.getMetaCreateMs();
                    status = MomentJs.humanise(age) + " behind";
                }
            }
        }
        return status;
    }
}
