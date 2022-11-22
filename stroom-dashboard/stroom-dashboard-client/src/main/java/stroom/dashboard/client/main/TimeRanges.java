package stroom.dashboard.client.main;

import stroom.query.api.v2.TimeRange;

public class TimeRanges {

    // Constants
    private TimeRanges() {
    }

    // All time
    public static final TimeRange ALL_TIME =
            new TimeRange("All time", null, null);

    // Recent
    public static final TimeRange LAST_MINUTE =
            new TimeRange("Last minute", "now()-1m", "now()");
    public static final TimeRange LAST_10_MINUTES =
            new TimeRange("Last 10 minutes", "now()-10m", "now()");
    public static final TimeRange LAST_30_MINUTES =
            new TimeRange("Last 30 minutes", "now()-30m", "now()");
    public static final TimeRange LAST_60_MINUTES =
            new TimeRange("Last 60 minutes", "now()-1h", "now()");
    public static final TimeRange LAST_24_HOURS =
            new TimeRange("Last 24 hours", "now()-24h", "now()");

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

    public static final TimeRange[] RECENT_RANGES = new TimeRange[]{
            LAST_MINUTE,
            LAST_10_MINUTES,
            LAST_30_MINUTES,
            LAST_60_MINUTES,
            LAST_24_HOURS
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
            LAST_MINUTE,
            LAST_10_MINUTES,
            LAST_30_MINUTES,
            LAST_60_MINUTES,
            LAST_24_HOURS,
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
