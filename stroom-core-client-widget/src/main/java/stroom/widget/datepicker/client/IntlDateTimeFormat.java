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

package stroom.widget.datepicker.client;

import com.google.gwt.core.client.JavaScriptObject;

import java.util.Objects;

public class IntlDateTimeFormat {

    public static final String[] DEFAULT_LOCALE = new String[]{"default"};

    public static class FormatOptions {

        private final String locale;
        private final String calendar;
        private final String numberingSystem;
        private final String timeZone;
        private final HourCycle hourCycle;
        private final Boolean hour12;
        private final Weekday weekday;
        private final Era era;
        private final Year year;
        private final Month month;
        private final Day day;
        private final DayPeriod dayPeriod;
        private final Hour hour;
        private final Minute minute;
        private final Second second;
        private final Integer fractionalSecondDigits;
        private final TimeZoneName timeZoneName;
        private final DateStyle dateStyle;
        private final TimeStyle timeStyle;

        public FormatOptions(final String locale,
                             final String calendar,
                             final String numberingSystem,
                             final String timeZone,
                             final HourCycle hourCycle,
                             final Boolean hour12,
                             final Weekday weekday,
                             final Era era,
                             final Year year,
                             final Month month,
                             final Day day,
                             final DayPeriod dayPeriod,
                             final Hour hour,
                             final Minute minute,
                             final Second second,
                             final Integer fractionalSecondDigits,
                             final TimeZoneName timeZoneName,
                             final DateStyle dateStyle,
                             final TimeStyle timeStyle) {
            this.locale = locale;
            this.calendar = calendar;
            this.numberingSystem = numberingSystem;
            this.timeZone = timeZone;
            this.hourCycle = hourCycle;
            this.hour12 = hour12;
            this.weekday = weekday;
            this.era = era;
            this.year = year;
            this.month = month;
            this.day = day;
            this.dayPeriod = dayPeriod;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.fractionalSecondDigits = fractionalSecondDigits;
            this.timeZoneName = timeZoneName;
            this.dateStyle = dateStyle;
            this.timeStyle = timeStyle;
        }

        public String getLocale() {
            return locale;
        }

        public String getCalendar() {
            return calendar;
        }

        public String getNumberingSystem() {
            return numberingSystem;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public HourCycle getHourCycle() {
            return hourCycle;
        }

        public Boolean getHour12() {
            return hour12;
        }

        public Weekday getWeekday() {
            return weekday;
        }

        public Era getEra() {
            return era;
        }

        public Year getYear() {
            return year;
        }

        public Month getMonth() {
            return month;
        }

        public Day getDay() {
            return day;
        }

        public DayPeriod getDayPeriod() {
            return dayPeriod;
        }

        public Hour getHour() {
            return hour;
        }

        public Minute getMinute() {
            return minute;
        }

        public Second getSecond() {
            return second;
        }

        public Integer getFractionalSecondDigits() {
            return fractionalSecondDigits;
        }

        public TimeZoneName getTimeZoneName() {
            return timeZoneName;
        }

        public DateStyle getDateStyle() {
            return dateStyle;
        }

        public TimeStyle getTimeStyle() {
            return timeStyle;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final FormatOptions that = (FormatOptions) o;
            return Objects.equals(locale, that.locale) &&
                    Objects.equals(calendar, that.calendar) &&
                    Objects.equals(numberingSystem, that.numberingSystem) &&
                    Objects.equals(timeZone, that.timeZone) &&
                    hourCycle == that.hourCycle &&
                    Objects.equals(hour12, that.hour12) &&
                    weekday == that.weekday &&
                    era == that.era &&
                    year == that.year &&
                    month == that.month &&
                    day == that.day &&
                    dayPeriod == that.dayPeriod &&
                    hour == that.hour &&
                    minute == that.minute &&
                    second == that.second &&
                    Objects.equals(fractionalSecondDigits, that.fractionalSecondDigits) &&
                    timeZoneName == that.timeZoneName &&
                    dateStyle == that.dateStyle &&
                    timeStyle == that.timeStyle;
        }

        @Override
        public int hashCode() {
            return Objects.hash(locale,
                    calendar,
                    numberingSystem,
                    timeZone,
                    hourCycle,
                    hour12,
                    weekday,
                    era,
                    year,
                    month,
                    day,
                    dayPeriod,
                    hour,
                    minute,
                    second,
                    fractionalSecondDigits,
                    timeZoneName,
                    dateStyle,
                    timeStyle);
        }

        @Override
        public String toString() {
            return "FormatOptions{" +
                    "locale='" + locale + '\'' +
                    ", calendar='" + calendar + '\'' +
                    ", numberingSystem='" + numberingSystem + '\'' +
                    ", timeZone='" + timeZone + '\'' +
                    ", hourCycle=" + hourCycle +
                    ", hour12=" + hour12 +
                    ", weekday=" + weekday +
                    ", era=" + era +
                    ", year=" + year +
                    ", month=" + month +
                    ", day=" + day +
                    ", dayPeriod=" + dayPeriod +
                    ", hour=" + hour +
                    ", minute=" + minute +
                    ", second=" + second +
                    ", fractionalSecondDigits=" + fractionalSecondDigits +
                    ", timeZoneName=" + timeZoneName +
                    ", dateStyle=" + dateStyle +
                    ", timeStyle=" + timeStyle +
                    '}';
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private String locale;
            private String calendar;
            private String numberingSystem;
            private String timeZone;
            private HourCycle hourCycle;
            private Boolean hour12;
            private Weekday weekday;
            private Era era;
            private Year year;
            private Month month;
            private Day day;
            private DayPeriod dayPeriod;
            private Hour hour;
            private Minute minute;
            private Second second;
            private Integer fractionalSecondDigits;
            private TimeZoneName timeZoneName;
            private DateStyle dateStyle;
            private TimeStyle timeStyle;

            public Builder() {
            }

            public Builder(final FormatOptions options) {
                this.locale = options.locale;
                this.calendar = options.calendar;
                this.numberingSystem = options.numberingSystem;
                this.timeZone = options.timeZone;
                this.hourCycle = options.hourCycle;
                this.hour12 = options.hour12;
                this.weekday = options.weekday;
                this.era = options.era;
                this.year = options.year;
                this.month = options.month;
                this.day = options.day;
                this.dayPeriod = options.dayPeriod;
                this.hour = options.hour;
                this.minute = options.minute;
                this.second = options.second;
                this.fractionalSecondDigits = options.fractionalSecondDigits;
                this.timeZoneName = options.timeZoneName;
                this.dateStyle = options.dateStyle;
                this.timeStyle = options.timeStyle;
            }

            public Builder locale(final String locale) {
                this.locale = locale;
                return this;
            }

            public Builder calendar(final String calendar) {
                this.calendar = calendar;
                return this;
            }

            public Builder numberingSystem(final String numberingSystem) {
                this.numberingSystem = numberingSystem;
                return this;
            }

            public Builder timeZone(final String timeZone) {
                this.timeZone = timeZone;
                return this;
            }

            public Builder hourCycle(final HourCycle hourCycle) {
                this.hourCycle = hourCycle;
                return this;
            }

            public Builder hour12(final Boolean hour12) {
                this.hour12 = hour12;
                return this;
            }

            public Builder weekday(final Weekday weekday) {
                this.weekday = weekday;
                return this;
            }

            public Builder era(final Era era) {
                this.era = era;
                return this;
            }

            public Builder year(final Year year) {
                this.year = year;
                return this;
            }

            public Builder month(final Month month) {
                this.month = month;
                return this;
            }

            public Builder day(final Day day) {
                this.day = day;
                return this;
            }

            public Builder dayPeriod(final DayPeriod dayPeriod) {
                this.dayPeriod = dayPeriod;
                return this;
            }

            public Builder hour(final Hour hour) {
                this.hour = hour;
                return this;
            }

            public Builder minute(final Minute minute) {
                this.minute = minute;
                return this;
            }

            public Builder second(final Second second) {
                this.second = second;
                return this;
            }

            public Builder fractionalSecondDigits(final Integer fractionalSecondDigits) {
                this.fractionalSecondDigits = fractionalSecondDigits;
                return this;
            }

            public Builder timeZoneName(final TimeZoneName timeZoneName) {
                this.timeZoneName = timeZoneName;
                return this;
            }

            public Builder dateStyle(final DateStyle dateStyle) {
                this.dateStyle = dateStyle;
                return this;
            }

            public Builder timeStyle(final TimeStyle timeStyle) {
                this.timeStyle = timeStyle;
                return this;
            }

            public FormatOptions build() {
                return new FormatOptions(
                        locale,
                        calendar,
                        numberingSystem,
                        timeZone,
                        hourCycle,
                        hour12,
                        weekday,
                        era,
                        year,
                        month,
                        day,
                        dayPeriod,
                        hour,
                        minute,
                        second,
                        fractionalSecondDigits,
                        timeZoneName,
                        dateStyle,
                        timeStyle);
            }
        }


        public enum Weekday {
            NARROW("narrow"),
            SHORT("short"),
            LONG("long");

            private final String name;

            Weekday(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum Era {
            NARROW("narrow"),
            SHORT("short"),
            LONG("long");

            private final String name;

            Era(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum Year {
            TWO_DIGIT("2-digit"),
            NUMERIC("numeric");

            private final String name;

            Year(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum Month {
            TWO_DIGIT("2-digit"),
            NUMERIC("numeric"),
            NARROW("narrow"),
            SHORT("short"),
            LONG("long");

            private final String name;

            Month(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum Day {
            TWO_DIGIT("2-digit"),
            NUMERIC("numeric");

            private final String name;

            Day(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum DayPeriod {
            NARROW("narrow"),
            SHORT("short"),
            LONG("long");

            private final String name;

            DayPeriod(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum Hour {
            TWO_DIGIT("2-digit"),
            NUMERIC("numeric");

            private final String name;

            Hour(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum Minute {
            TWO_DIGIT("2-digit"),
            NUMERIC("numeric");

            private final String name;

            Minute(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum Second {
            TWO_DIGIT("2-digit"),
            NUMERIC("numeric");

            private final String name;

            Second(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum TimeZoneName {
            SHORT("short"),
            LONG("long"),
            SHORT_OFFSET("shortOffset"),
            LONG_OFFSET("longOffset"),
            SHORT_GENERIC("shortGeneric"),
            LONG_GENERIC("longGeneric");

            private final String name;

            TimeZoneName(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum HourCycle {
            H11("h11"),
            H12("h12"),
            H23("h23"),
            H24("h24");

            private final String name;

            HourCycle(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum DateStyle {
            FULL("full"),
            LONG("long"),
            MEDIUM("medium"),
            SHORT("short");

            private final String name;

            DateStyle(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }

        public enum TimeStyle {
            FULL("full"),
            LONG("long"),
            MEDIUM("medium"),
            SHORT("short");

            private final String name;

            TimeStyle(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }
    }

    public static String format(final UTCDate date, final String[] locales, final FormatOptions options) {
        final JsFormatOptions jsFormatOptions = JsFormatOptions.create(
                options.locale,
                options.calendar,
                options.numberingSystem,
                options.timeZone,
                options.hourCycle == null
                        ? null
                        : options.hourCycle.getName(),
                options.hour12,
                options.weekday == null
                        ? null
                        : options.weekday.getName(),
                options.era == null
                        ? null
                        : options.era.getName(),
                options.year == null
                        ? null
                        : options.year.getName(),
                options.month == null
                        ? null
                        : options.month.getName(),
                options.day == null
                        ? null
                        : options.day.getName(),
                options.dayPeriod == null
                        ? null
                        : options.dayPeriod.getName(),
                options.hour == null
                        ? null
                        : options.hour.getName(),
                options.minute == null
                        ? null
                        : options.minute.getName(),
                options.second == null
                        ? null
                        : options.second.getName(),
                options.fractionalSecondDigits,
                options.timeZoneName == null
                        ? null
                        : options.timeZoneName.getName(),
                options.dateStyle == null
                        ? null
                        : options.dateStyle.getName(),
                options.timeStyle == null
                        ? null
                        : options.timeStyle.getName());
        return format(date, locales, jsFormatOptions);
    }

    private static native String format(final UTCDate date, final String[] locales, final JsFormatOptions options) /*-{
        return new Intl.DateTimeFormat(locales, options).format(date);
    }-*/;

    public static native String[] getTimeZones() /*-{
        return Intl.supportedValuesOf('timeZone');
    }-*/;

    public static native String getTimeZone() /*-{
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }-*/;

//    /**
//     * A timezone offset in minutes
//     */
//    public static native int getTimeZoneOffset(final JsDate date, final String timeZone) /*-{
//        var isoDateString = date.toISOString();
//        var utcDate = new Date(isoDateString);
//        var localDateString = date.toLocaleString('en-GB', { timeZone: timeZone });
//        var tzDate = new Date(localDateString);
//        return (tzDate.getTime() - utcDate.getTime()) / 6e4;
//    }-*/;

    public static native int getLanguageCount() /*-{
        return navigator.languages.length;
    }-*/;

    public static native String getLanguage(int i) /*-{
        return navigator.languages[i];
    }-*/;

    private static class JsFormatOptions extends JavaScriptObject {

        protected JsFormatOptions() {
        }

        private static native JsFormatOptions create(final String locale,
                                                     final String calendar,
                                                     final String numberingSystem,
                                                     final String timeZone,
                                                     final String hourCycle,
                                                     final Boolean hour12,
                                                     final String weekday,
                                                     final String era,
                                                     final String year,
                                                     final String month,
                                                     final String day,
                                                     final String dayPeriod,
                                                     final String hour,
                                                     final String minute,
                                                     final String second,
                                                     final Integer fractionalSecondDigits,
                                                     final String timeZoneName,
                                                     final String dateStyle,
                                                     final String timeStyle) /*-{
            var options = {};
            if (locale) {
                options.locale = locale;
            }
            if (calendar) {
                options.calendar = calendar;
            }
            if (numberingSystem) {
                options.numberingSystem = numberingSystem;
            }
            if (timeZone) {
                options.timeZone = timeZone;
            }
            if (hourCycle) {
                options.hourCycle = hourCycle;
            }
            if (hour12) {
                options.hour12 = hour12;
            }
            if (weekday) {
                options.weekday = weekday;
            }
            if (era) {
                options.era = era;
            }
            if (year) {
                options.year = year;
            }
            if (month) {
                options.month = month;
            }
            if (day) {
                options.day = day;
            }
            if (dayPeriod) {
                options.dayPeriod = dayPeriod;
            }
            if (hour) {
                options.hour = hour;
            }
            if (minute) {
                options.minute = minute;
            }
            if (second) {
                options.second = second;
            }
            if (fractionalSecondDigits) {
                options.fractionalSecondDigits = fractionalSecondDigits;
            }
            if (timeZoneName) {
                options.timeZoneName = timeZoneName;
            }
            if (dateStyle) {
                options.dateStyle = dateStyle;
            }
            if (timeStyle) {
                options.timeStyle = timeStyle;
            }
            return options;
        }-*/;
    }
}
