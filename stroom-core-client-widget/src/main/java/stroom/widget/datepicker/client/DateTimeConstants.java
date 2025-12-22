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

import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Day;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Month;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Weekday;

import com.google.gwt.core.client.GWT;

public class DateTimeConstants {

    public static final int DAYS_IN_WEEK = 7;

    public static final int MONTHS_IN_YEAR = 12;

    private static final int MAX_DAYS_IN_MONTH = 31;

    private final String[] languages;
    private final String[] dayOfWeekNames;
    private final String[] dayOfMonthNames;
    private final String[] monthNames;

    public DateTimeConstants() {
        languages = new String[IntlDateTimeFormat.getLanguageCount()];
        for (int i = 0; i < languages.length; i++) {
            languages[i] = IntlDateTimeFormat.getLanguage(i);
            GWT.log(languages[i]);
        }

        dayOfWeekNames = new String[DAYS_IN_WEEK];
        for (int i = 0; i < dayOfWeekNames.length; i++) {
            final UTCDate date = UTCDate.create(2000, 0, i + 1);
            final String dayOfWeekName = IntlDateTimeFormat.format(date, languages,
                    FormatOptions.builder().weekday(Weekday.SHORT).build());
            dayOfWeekNames[date.getDay()] = dayOfWeekName;
        }

        dayOfMonthNames = new String[MAX_DAYS_IN_MONTH + 1];
        for (int i = 1; i < dayOfMonthNames.length; i++) {
            final UTCDate date = UTCDate.create(2000, 0, i);
            final String dayOfMonthName = IntlDateTimeFormat.format(date, languages,
                    FormatOptions.builder().day(Day.NUMERIC).build());
            dayOfMonthNames[i] = dayOfMonthName;
        }

        monthNames = new String[MONTHS_IN_YEAR];
        for (int i = 0; i < monthNames.length; i++) {
            final UTCDate date = UTCDate.create(2000, i, 1);
            final String monthName = IntlDateTimeFormat.format(date, languages,
                    FormatOptions.builder().month(Month.LONG).build());
            monthNames[i] = monthName;
        }
    }

    public String[] getLanguages() {
        return languages;
    }

    public String[] getDayOfWeekNames() {
        return dayOfWeekNames;
    }

    public String[] getDayOfMonthNames() {
        return dayOfMonthNames;
    }

    public String[] getMonthNames() {
        return monthNames;
    }
}
