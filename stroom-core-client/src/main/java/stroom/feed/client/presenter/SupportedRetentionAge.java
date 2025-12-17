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

package stroom.feed.client.presenter;

import stroom.docref.HasDisplayValue;

public enum SupportedRetentionAge implements HasDisplayValue {
    RT_FOREVER("Forever", null),
    RT_1("1 Day", 1),
    RT_2("2 Days", 2),
    RT_4("4 Days", 4),
    RT_7("1 Week", 7),
    RT_31(
            "1 Month", 31),
    RT_93("3 Months", 93),
    RT_182("6 Months", 182),
    RT_365("1 Year", 365),
    RT_730("2 Years",
            730),
    RT_1095("3 Years", 1095),
    RT_1460("4 Years", 1460),
    RT_1825("5 Years", 1825),
    RT_3650("10 Years", 3650);

    private final String displayValue;
    private final Integer days;

    SupportedRetentionAge(final String displayValue, final Integer days) {
        this.displayValue = displayValue;
        this.days = days;
    }

    public static final SupportedRetentionAge get(final Integer days) {
        if (days == null) {
            return RT_FOREVER;
        }
        return SupportedRetentionAge.valueOf("RT_" + days);
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public Integer getDays() {
        return days;
    }

    @Override
    public String toString() {
        return displayValue;
    }
}
