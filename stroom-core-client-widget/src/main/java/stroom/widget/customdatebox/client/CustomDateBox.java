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

package stroom.widget.customdatebox.client;

import stroom.util.shared.HasLongValue;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.datepicker.client.DateBox;
import com.google.gwt.user.datepicker.client.DatePicker;

import java.util.Date;

public class CustomDateBox extends DateBox implements HasLongValue {

    /**
     * Create a date box with a new {@link DatePicker}.
     */
    public CustomDateBox() {
        super(new CustomDatePicker(), null, new CustomDateBoxFormat());
    }

    @Override
    public Long getLongValue() {
        return toLongFromDate(getValue());
    }

    @Override
    public void setLongValue(final Long date) {
        setValue(toDateFromLong(date));
    }

    private Long toLongFromDate(final Date date) {
        if (date == null) {
            return null;
        }
        return date.getTime();
    }

    private Date toDateFromLong(final Long date) {
        if (date == null) {
            return null;
        }
        return new Date(date.longValue());
    }

    public void setLongValue(final Long date, final boolean fireEvents) {
        setValue(toDateFromLong(date), fireEvents);
    }

    @Override
    public void setValue(final Date date, final boolean fireEvents) {
        if (!fireEvents && date != null) {
            ((CustomDateBoxFormat) getFormat()).setLastDate(date);
        }

        super.setValue(date, fireEvents);
    }

    public static class CustomDateBoxFormat implements Format {

        /**
         * Default style name added when the date box has a format error.
         */
        private static final String DATE_BOX_FORMAT_ERROR = "dateBoxFormatError";
        private final DateTimeFormat dateTimeFormat = new CustomDateTimeFormat();
        private Date lastDate;

        @SuppressWarnings("deprecation")
        public CustomDateBoxFormat() {
            final Date now = new Date();
            lastDate = new Date(now.getYear(), now.getMonth(), now.getDate());
        }

        @SuppressWarnings("deprecation")
        @Override
        public String format(final DateBox dateBox, final Date date) {
            if (date == null) {
                return "";
            } else {
                Date newDate = null;

                if (lastDate != null) {
                    long millis = lastDate.getTime();
                    millis = millis % 1000;

                    newDate = new Date(date.getYear(), date.getMonth(), date.getDate(), lastDate.getHours(),
                            lastDate.getMinutes(), lastDate.getSeconds());
                    newDate.setTime(newDate.getTime() + millis);

                } else {
                    newDate = new Date(date.getYear(), date.getMonth(), date.getDate());
                }

                return dateTimeFormat.format(newDate);
            }
        }

        @Override
        public Date parse(final DateBox dateBox, final String dateText, final boolean reportError) {
            Date date = null;
            try {
                if (dateText.length() > 0) {
                    date = dateTimeFormat.parse(dateText);
                    lastDate = date;
                }
            } catch (final IllegalArgumentException exception) {
                if (reportError) {
                    dateBox.addStyleName(DATE_BOX_FORMAT_ERROR);
                }

                return null;
            }

            return date;
        }

        @Override
        public void reset(final DateBox dateBox, final boolean abandon) {
            dateBox.removeStyleName(DATE_BOX_FORMAT_ERROR);
        }

        public void setLastDate(final Date lastDate) {
            this.lastDate = lastDate;
        }
    }
}
