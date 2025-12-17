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

package stroom.query.client.view;

import stroom.query.api.TimeRange;

public class TimeRanges {

    // Constants
    private TimeRanges() {
    }

    // All time
    public static final TimeRange ALL_TIME =
            new TimeRange("All time", null, null);

    // Relative
    public static final TimeRange RELATIVE_MINUTE =
            new TimeRange("1 Minute", "now()-1m", "now()");
    public static final TimeRange RELATIVE_10_MINUTES =
            new TimeRange("10 minutes", "now()-10m", "now()");
    public static final TimeRange RELATIVE_30_MINUTES =
            new TimeRange("30 minutes", "now()-30m", "now()");
    public static final TimeRange RELATIVE_60_MINUTES =
            new TimeRange("60 minutes", "now()-1h", "now()");
    public static final TimeRange RELATIVE_24_HOURS =
            new TimeRange("24 hours", "now()-24h", "now()");

    // Present
    public static final TimeRange THIS_HOUR =
            new TimeRange("This hour", "hour()", "now()");
    public static final TimeRange TODAY =
            new TimeRange("Today", "day()", "now()");
    public static final TimeRange THIS_WEEK =
            new TimeRange("This week", "week()", "now()");
    public static final TimeRange THIS_MONTH =
            new TimeRange("This month", "month()", "now()");
    public static final TimeRange THIS_YEAR =
            new TimeRange("This year", "year()", "now()");

    // Past
    public static final TimeRange LAST_HOUR =
            new TimeRange("Last hour", "hour()-1h", "hour()");
    public static final TimeRange YESTERDAY =
            new TimeRange("Yesterday", "day()-1d", "day()");
    public static final TimeRange LAST_WEEK =
            new TimeRange("Last week", "week()-1w", "week()");
    public static final TimeRange LAST_MONTH =
            new TimeRange("Last month", "month()-1M", "month()");
    public static final TimeRange LAST_YEAR =
            new TimeRange("Last year", "year()-1y", "year()");

    public static final TimeRange[] RELATIVE_RANGES = new TimeRange[]{
            RELATIVE_MINUTE,
            RELATIVE_10_MINUTES,
            RELATIVE_30_MINUTES,
            RELATIVE_60_MINUTES,
            RELATIVE_24_HOURS
    };

    public static final TimeRange[] PRESENT_RANGES = new TimeRange[]{
            THIS_HOUR,
            TODAY,
            THIS_WEEK,
            THIS_MONTH,
            THIS_YEAR
    };

    public static final TimeRange[] PAST_RANGES = new TimeRange[]{
            LAST_HOUR,
            YESTERDAY,
            LAST_WEEK,
            LAST_MONTH,
            LAST_YEAR
    };

    public static final TimeRange[] ALL_RANGES = new TimeRange[]{
            ALL_TIME,
            RELATIVE_MINUTE,
            RELATIVE_10_MINUTES,
            RELATIVE_30_MINUTES,
            RELATIVE_60_MINUTES,
            RELATIVE_24_HOURS,
            THIS_HOUR,
            TODAY,
            THIS_WEEK,
            THIS_MONTH,
            THIS_YEAR,
            LAST_HOUR,
            YESTERDAY,
            LAST_WEEK,
            LAST_MONTH,
            LAST_YEAR
    };
}
