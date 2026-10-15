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

package stroom.meta.shared;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaRow {

    @Test
    void testGetAttributeValue() {
        final MetaRow metaRow = new MetaRow(null, null, Map.of("Rec Read", "10"));

        assertThat(metaRow.getAttributeValue("Rec Read"))
                .isEqualTo("10");
        assertThat(metaRow.getAttributeValue("Rec Write"))
                .isNull();
    }

    @Test
    void testGetAttributeValue_noAttributes() {
        // A row without attributes (e.g. from a reply that leaves them out) has no values, rather
        // than throwing
        final MetaRow metaRow = new MetaRow(null, null, null);

        assertThat(metaRow.getAttributeValue("Rec Read"))
                .isNull();
    }
}
