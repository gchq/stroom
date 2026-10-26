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
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestActivitySummary {

    @Test
    void testNoActivity() {
        assertThat(ActivitySummary.toHtml(null).asString())
                .isEqualTo("<h2>Current Activity</h2><b>none</b>");
    }

    @Test
    void testShownProperties() {
        final Activity activity = activity(List.of(
                prop("Ticket", "T-1", true),
                prop("Hidden", "secret", false)));
        assertThat(ActivitySummary.toHtml(activity).asString())
                .isEqualTo("<h2>Current Activity</h2><b>Ticket: </b>T-1<br>");
    }

    @Test
    void testValuesAreText() {
        // Regression test: what the user typed was shown as HTML
        final Activity activity = activity(List.of(prop("<i>Name</i>", "<img src=x onerror=alert(1)>", true)));
        assertThat(ActivitySummary.toHtml(activity).asString())
                .isEqualTo("<h2>Current Activity</h2><b>&lt;i&gt;Name&lt;/i&gt;: </b>"
                           + "&lt;img src=x onerror=alert(1)&gt;<br>");
    }

    private static Prop prop(final String name, final String value, final boolean shown) {
        return new Prop(name, name, null, null, value, shown, true);
    }

    private static Activity activity(final List<Prop> props) {
        return Activity.builder().details(new ActivityDetails(props)).build();
    }
}
