/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.tooltip.client.presenter;

public final class TooltipUtil {
    private TooltipUtil() {
        // Utility class.
    }

    public static void addHeading(final StringBuilder buffer, final String heading) {
        buffer.append("<b>");
        buffer.append(heading);
        buffer.append("</b><br/>");
    }

    public static void addRowData(final StringBuilder buffer, final String heading, final Object value) {
        addRowData(buffer, heading, value, false);
    }

    public static void addRowData(final StringBuilder buffer, final String heading, final Object value,
                                  final boolean showBlank) {
        if (value != null) {
            final String s = String.valueOf(value);
            if (s.length() > 0 || showBlank) {
                buffer.append(heading);
                buffer.append(" : ");
                buffer.append(s);
                buffer.append("<br/>");
            }
        } else {
            if (showBlank) {
                buffer.append(heading);
                buffer.append(" : <br/>");
            }
        }
    }

    public static void addRowData(final StringBuilder buffer, final String value) {
        if (value != null && value.length() > 0) {
            buffer.append(value);
            buffer.append("<br/>");
        }
    }

    public static void addBreak(final StringBuilder buffer) {
        buffer.append("<br/>");
    }
}
