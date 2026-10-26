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

package stroom.dashboard.client.vis;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisControls {

    @Test
    void theControlsAVisualisationSendsAreRead() {
        final Map<String, Object> sent = new HashMap<>();
        sent.put("zoom", true);
        sent.put("zoomed", false);
        sent.put("range", true);
        sent.put("rangeMode", true);
        sent.put("selectable", true);
        sent.put("selected", "2026-10-24 → 2026-10-26");
        sent.put("note", "First 1000 rows");

        assertThat(VisControls.of(sent)).isEqualTo(new VisControls(true, false, true, true, true,
                "2026-10-24 → 2026-10-26", "First 1000 rows"));
    }

    @Test
    void onlyWhatIsExpectedIsKept() {
        // Any script in the frame can send them.
        final Map<String, Object> sent = new HashMap<>();
        sent.put("zoom", "true");
        sent.put("range", 1);
        sent.put("selected", 42);
        sent.put("note", "  ");
        sent.put("script", "<script>alert(1)</script>");

        assertThat(VisControls.of(sent)).isEqualTo(VisControls.NONE);
        assertThat(VisControls.of((Map<String, Object>) null)).isEqualTo(VisControls.NONE);
    }

    @Test
    void textIsKeptToOneShortLine() {
        assertThat(VisControls.text("alice\nand\t\u0000 bob ")).isEqualTo("alice and bob");
        final String shortened = VisControls.text("x".repeat(VisControls.MAX_TEXT + 50));
        assertThat(shortened).hasSize(VisControls.MAX_TEXT).endsWith("…");
        assertThat(VisControls.text(null)).isNull();
        assertThat(VisControls.text("")).isNull();
    }
}
