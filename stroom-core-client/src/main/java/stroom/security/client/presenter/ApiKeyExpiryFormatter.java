/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.client.presenter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

final class ApiKeyExpiryFormatter {

    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1_000;

    private ApiKeyExpiryFormatter() {
    }

    /// Formats an expiry date and indicates expired keys or expiry within 30 days.
    ///
    /// @param expireTimeMs the expiry instant, or null for a key without an expiry
    /// @param nowMs the current time, supplied explicitly for consistent boundary tests
    /// @param formattedDate the existing user-preference-aware date and duration text
    /// @return escaped date text with a visible expiry status when appropriate
    static SafeHtml format(final Long expireTimeMs, final long nowMs, final String formattedDate) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        final String date = formattedDate == null ? "" : formattedDate;
        if (expireTimeMs != null && expireTimeMs <= nowMs) {
            builder.appendHtmlConstant("<em>Expired: ")
                    .appendEscaped(date)
                    .appendHtmlConstant("</em>");
        } else if (expireTimeMs != null
                   && (nowMs > Long.MAX_VALUE - THIRTY_DAYS_MS
                       || expireTimeMs <= nowMs + THIRTY_DAYS_MS)) {
            builder.appendHtmlConstant("<strong>Expires soon: ")
                    .appendEscaped(date)
                    .appendHtmlConstant("</strong>");
        } else {
            builder.appendEscaped(date);
        }
        return builder.toSafeHtml();
    }
}
