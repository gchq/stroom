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

package stroom.annotation.client;

import stroom.preferences.client.DateTimeFormatter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.datepicker.client.CalendarUtil;
import com.google.inject.Inject;

import java.util.Date;

public class DurationLabel {

    private static final long ONE_SECOND = 1000;
    private static final long ONE_MINUTE = ONE_SECOND * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public DurationLabel(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    public SafeHtml getDurationLabel(final long time, final Date now) {
        final HtmlBuilder builder = new HtmlBuilder();
        append(builder, time, now);
        return builder.toSafeHtml();
    }

    public void append(final HtmlBuilder builder, final long time, final Date now) {
        span(builder, "annotationDurationLabel", time, now);
    }

    public void span(final HtmlBuilder builder, final String className, final long time, final Date now) {
        builder.span(span -> span.append(getDuration(time, now)),
                Attribute.className(className),
                Attribute.title(dateTimeFormatter.format(time)));
    }

    public void div(final HtmlBuilder builder, final String className, final long time, final Date now) {
        builder.div(div -> div.append(getDuration(time, now)),
                Attribute.className(className),
                Attribute.title(dateTimeFormatter.format(time)));
    }

    private String getDuration(final long time, final Date now) {
        final Date start = new Date(time);
        final int days = CalendarUtil.getDaysBetween(start, now);
        if (days == 1) {
            return "yesterday";
        } else if (days > 365) {
            final int years = days / 365;
            if (years == 1) {
                return "a year ago";
            } else {
                return years + "years ago";
            }
        } else if (days > 1) {
            return days + " days ago";
        }

        final long diff = now.getTime() - time;
        if (diff > ONE_HOUR) {
            final int hours = (int) (diff / ONE_HOUR);
            if (hours == 1) {
                return "an hour ago";
            } else if (hours > 1) {
                return hours + " hours ago";
            }
        }

        if (diff > ONE_MINUTE) {
            final int minutes = (int) (diff / ONE_MINUTE);
            if (minutes == 1) {
                return "a minute ago";
            } else if (minutes > 1) {
                return minutes + " minutes ago";
            }
        }

        if (diff > ONE_SECOND) {
            final int seconds = (int) (diff / ONE_SECOND);
            if (seconds == 1) {
                return "a second ago";
            } else if (seconds > 1) {
                return seconds + " seconds ago";
            }
        }

        return "just now";
    }
}
