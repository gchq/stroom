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

class TestAbstractSelectionListModel {

    @Test
    void testFilterIgnoresCase() {
        final TestModel model = createModel();

        assertThat(filter(model, "content")).containsExactly("Content.Text", "content.vector");
        assertThat(filter(model, "Content")).containsExactly("Content.Text", "content.vector");
        assertThat(filter(model, "TEXT")).containsExactly("Content.Text");
    }

    @Test
    void testFilterWithNoMatches() {
        assertThat(filter(createModel(), "Missing")).isEmpty();
    }

    @Test
    void testEmptyFilterMatchesEverything() {
        final TestModel model = createModel();

        assertThat(filter(model, null)).containsExactly("Content.Text", "content.vector", "Host");
        assertThat(filter(model, "")).containsExactly("Content.Text", "content.vector", "Host");
    }

    private static TestModel createModel() {
        final TestModel model = new TestModel();
        model.addItems(List.of("Content.Text", "content.vector", "Host"));
        return model;
    }

    private static List<String> filter(final TestModel model, final String filter) {
        final List<String> labels = new ArrayList<>();
        model.onRangeChange(null, filter, true, null, (final ResultPage<SimpleSelectionItemWrapper<String>> page) ->
                page.getValues().forEach(item -> labels.add(item.getLabel())));
        return labels;
    }

    // A minimal concrete model whose items are labelled with their own text
    private static class TestModel extends AbstractSelectionListModel<String, SimpleSelectionItemWrapper<String>> {

        @Override
        public SimpleSelectionItemWrapper<String> wrap(final String item) {
            return new SimpleSelectionItemWrapper<>(item, item);
        }

        @Override
        public String unwrap(final SimpleSelectionItemWrapper<String> selectionItem) {
            return selectionItem == null
                    ? null
                    : selectionItem.getItem();
        }
    }
}
