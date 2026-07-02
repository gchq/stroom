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

import com.google.inject.Inject;

public class DurationLabel {

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public DurationLabel(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    public void append(final HtmlBuilder builder, final long time, final long nowMs) {
        span(builder, "annotationDurationLabel", time, nowMs);
    }

    public void span(final HtmlBuilder builder, final String className, final long time, final long nowMs) {
        builder.span(span -> span.append(dateTimeFormatter.formatRelative(time, nowMs)),
                Attribute.className(className),
                Attribute.title(dateTimeFormatter.format(time)));
    }

    public void div(final HtmlBuilder builder, final String className, final long time, final long nowMs) {
        builder.div(div -> div.append(dateTimeFormatter.formatRelative(time, nowMs)),
                Attribute.className(className),
                Attribute.title(dateTimeFormatter.format(time)));
    }
}
