/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.client;


import stroom.preferences.client.DateTimeFormatter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;

public class ExpiryFormatter {

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public ExpiryFormatter(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    public SafeHtml formatWithDuration(final Long ms, final long alertThresholdMs) {
        final SafeHtml safeHtml;
        if (ms == null) {
            safeHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            final long nowMs = System.currentTimeMillis();
            if (ms < nowMs + alertThresholdMs) {
                final String text;
                if (ms < nowMs) {
                    // Already expired
                    text = dateTimeFormatter.format(ms) + " (EXPIRED)";
                } else {
                    // Expiring in < 30 days
                    text = dateTimeFormatter.formatWithDuration(ms);
                }
                safeHtml = HtmlBuilder.builder()
                        .span(text, Attribute.className("dataGridAlertText"))
                        .toSafeHtml();
            } else {
                safeHtml = SafeHtmlUtils.fromTrustedString(dateTimeFormatter.formatWithDuration(ms));
            }
        }
        return safeHtml;
    }
}
