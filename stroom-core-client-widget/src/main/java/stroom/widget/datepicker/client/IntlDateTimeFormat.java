/*
 * Copyright 2024 Crown Copyright
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

        private final String timeZone;
        private final Weekday weekday;
        private final Year year;
        private final Month month;
        private final Day day;
        private final Hour hour;
        private final Minute minute;
        private final Second second;
        private final Integer fractionalSecondDigits;
        private final TimeZoneName timeZoneName;

        public FormatOptions(final String timeZone,
                             final Weekday weekday,
                             final Year year,
                             final Month month,
                             final Day day,
                             final Hour hour,
                             final Minute minute,
                             final Second second,
                             final Integer fractionalSecondDigits,
                             final TimeZoneName timeZoneName) {
            this.timeZone = timeZone;
            this.weekday = weekday;
            this.year = year;
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.fractionalSecondDigits = fractionalSecondDigits;
            this.timeZoneName = timeZoneName;
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
            return Objects.equals(timeZone, that.timeZone) &&
                    weekday == that.weekday &&
                    year == that.year &&
                    month == that.month &&
                    day == that.day &&
                    hour == that.hour &&
                    minute == that.minute &&
                    second == that.second &&
                    Objects.equals(fractionalSecondDigits, that.fractionalSecondDigits) &&
                    timeZoneName == that.timeZoneName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timeZone,
                    weekday,
                    year,
                    month,
                    day,
                    hour,
                    minute,
                    second,
                    fractionalSecondDigits,
                    timeZoneName);
        }

        @Override
        public String toString() {
            return "FormatOptions{" +
                    "timeZone='" + timeZone + '\'' +
                    ", weekday=" + weekday +
                    ", year=" + year +
                    ", month=" + month +
                    ", day=" + day +
                    ", hour=" + hour +
                    ", minute=" + minute +
                    ", second=" + second +
                    ", fractionalSecondDigits=" + fractionalSecondDigits +
                    ", timeZoneName=" + timeZoneName +
                    '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private String timeZone;
            private Weekday weekday;
            private Year year;
            private Month month;
            private Day day;
            private Hour hour;
            private Minute minute;
            private Second second;
            private Integer fractionalSecondDigits;
            private TimeZoneName timeZoneName;

            public Builder timeZone(final String timeZone) {
                this.timeZone = timeZone;
                return this;
            }

            public Builder weekday(final Weekday weekday) {
                this.weekday = weekday;
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

            public FormatOptions build() {
                return new FormatOptions(
                        timeZone,
                        weekday,
                        year,
                        month,
                        day,
                        hour,
                        minute,
                        second,
                        fractionalSecondDigits,
                        timeZoneName);
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
    }

    public static String format(final UTCDate date, final String[] locales, final FormatOptions options) {
        final JsFormatOptions jsFormatOptions = JsFormatOptions.create(
                options.timeZone,
                options.weekday == null
                        ? null
                        : options.weekday.getName(),
                options.year == null
                        ? null
                        : options.year.getName(),
                options.month == null
                        ? null
                        : options.month.getName(),
                options.day == null
                        ? null
                        : options.day.getName(),
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
                        : options.timeZoneName.getName());
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

    public static native int getLanguageCount() /*-{
        return navigator.languages.length;
    }-*/;

    public static native String getLanguage(int i) /*-{
        return navigator.languages[i];
    }-*/;

    private static class JsFormatOptions extends JavaScriptObject {

        protected JsFormatOptions() {
        }

        private static native JsFormatOptions create(final String timeZone,
                                                     final String weekday,
                                                     final String year,
                                                     final String month,
                                                     final String day,
                                                     final String hour,
                                                     final String minute,
                                                     final String second,
                                                     final Integer fractionalSecondDigits,
                                                     final String timeZoneName) /*-{
            var options = {};
            if (timeZone) {
                options.timeZone = timeZone;
            }
            if (weekday) {
                options.weekday = weekday;
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
            return options;
        }-*/;
    }
}
