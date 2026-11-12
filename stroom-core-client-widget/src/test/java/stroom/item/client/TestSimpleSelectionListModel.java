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

package stroom.item.client;

import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSimpleSelectionListModel {

    @Test
    void testFilterIgnoresCase() {
        final SimpleSelectionListModel<String> model = createModel();

        assertThat(filter(model, "content")).containsExactly("Content.Text", "content.vector");
        assertThat(filter(model, "Content")).containsExactly("Content.Text", "content.vector");
        assertThat(filter(model, "TEXT")).containsExactly("Content.Text");
    }

    @Test
    void testFilterWithNoMatches() {
        assertThat(filter(createModel(), "Missing")).isEmpty();
    }

    @Test
    void testBlankFilterMatchesEverything() {
        final SimpleSelectionListModel<String> model = createModel();

        assertThat(filter(model, null)).containsExactly("Content.Text", "content.vector", "Host");
        assertThat(filter(model, " ")).containsExactly("Content.Text", "content.vector", "Host");
    }

    private static SimpleSelectionListModel<String> createModel() {
        final SimpleSelectionListModel<String> model = new SimpleSelectionListModel<>();
        model.addItems(List.of("Content.Text", "content.vector", "Host"));
        return model;
    }

    private static List<String> filter(final SimpleSelectionListModel<String> model, final String filter) {
        final List<String> labels = new ArrayList<>();
        model.onRangeChange(null, filter, true, null, (final ResultPage<SimpleSelectionItemWrapper<String>> page) ->
                page.getValues().forEach(item -> labels.add(item.getLabel())));
        return labels;
    }
}
