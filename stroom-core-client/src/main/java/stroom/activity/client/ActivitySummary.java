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

package stroom.activity.client;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.Prop;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

/// The current activity's summary, as the explorer's activity button shows it: each property shown
/// in the selection, by name and value, as text (they are what the user typed, so they are escaped).
public final class ActivitySummary {

    private ActivitySummary() {
        // Static methods only
    }

    /// @param activity The current activity, or null for none.
    /// @return The summary.
    public static SafeHtml toHtml(final Activity activity) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<h2>Current Activity</h2>");
        if (activity != null && activity.getDetails() != null) {
            for (final Prop prop : activity.getDetails().getProperties()) {
                if (prop.isShowInSelection()) {
                    builder.appendHtmlConstant("<b>");
                    builder.appendEscaped(prop.getName() == null
                            ? ""
                            : prop.getName());
                    builder.appendHtmlConstant(": </b>");
                    builder.appendEscaped(prop.getValue() == null
                            ? ""
                            : prop.getValue());
                    builder.appendHtmlConstant("<br>");
                }
            }
        } else {
            builder.appendHtmlConstant("<b>none</b>");
        }
        return builder.toSafeHtml();
    }
}
