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

package stroom.streamstore.client.presenter;

import stroom.streamstore.shared.StreamType;
import stroom.docref.HasDisplayValue;

import java.util.Arrays;
import java.util.List;

public enum StreamListFilterTemplate implements HasDisplayValue {
    RAW_LAST_HOUR("Raw last hour", 1), RAW_LAST_DAY("Raw last day", 24), RAW_LAST_WEEK("Raw last week",
            168), PROCESSED_LAST_HOUR("Processed last hour", 1), PROCESSED_LAST_DAY("Processed last day",
            24), PROCESSED_LAST_WEEK("Processed last week", 168), ERRORS_LAST_HOUR("Errors last hour",
            1), ERRORS_LAST_DAY("Errors last day", 24), ERRORS_LAST_WEEK("Errors last week",
            168), ALL_LAST_HOUR("All last hour", 1), ALL_LAST_DAY("All last day",
            24), ALL_LAST_WEEK("All last week", 168);

    private final String displayValue;
    private final int hourPeriod;

    StreamListFilterTemplate(final String displayValue, final int hourPeriod) {
        this.displayValue = displayValue;
        this.hourPeriod = hourPeriod;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public List<StreamType> getStreamType(final StreamTypeUiManager streamTypeUiManager) {
        switch (this) {
            case RAW_LAST_HOUR:
            case RAW_LAST_DAY:
            case RAW_LAST_WEEK:
                return streamTypeUiManager.getRawStreamTypeList();
            case PROCESSED_LAST_HOUR:
            case PROCESSED_LAST_DAY:
            case PROCESSED_LAST_WEEK:
                return streamTypeUiManager.getProcessedStreamTypeList();
            case ERRORS_LAST_HOUR:
            case ERRORS_LAST_DAY:
            case ERRORS_LAST_WEEK:
                return Arrays.asList(StreamType.ERROR);
        }
        return null;
    }

    public int getHourPeriod() {
        return hourPeriod;
    }
}
