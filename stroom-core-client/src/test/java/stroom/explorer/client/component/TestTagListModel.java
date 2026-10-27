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

package stroom.explorer.client.component;

import stroom.util.shared.PageRequest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestTagListModel {

    @Test
    void displayFilter_neverEvenForManyTags() {
        // A quick filter would hide tags that are still chosen, which the dialog would act on unseen
        final TagListModel model = new TagListModel();
        model.addItems(tags(50));

        assertThat(model.displayFilter())
                .isFalse();
    }

    @Test
    void displayPager_onlyOverAHundredTags() {
        final TagListModel model = new TagListModel();
        model.addItems(tags(100));
        assertThat(model.displayPager())
                .isFalse();

        model.addItem("one-more");
        assertThat(model.displayPager())
                .isTrue();
    }

    @Test
    void onRangeChange_givesTheTagsInTheirOrder() {
        final TagListModel model = new TagListModel();
        model.addItems(List.of("beta", "alpha", "gamma"));

        assertThat(labels(model))
                .containsExactly("beta", "alpha", "gamma");
    }

    @Test
    void clear_removesEveryTag() {
        final TagListModel model = new TagListModel();
        model.addItems(List.of("alpha", "beta"));
        model.clear();

        assertThat(labels(model))
                .isEmpty();
    }

    @Test
    void tagsEqualByValue() {
        // A chosen tag stays chosen when the list is rebuilt with new wrappers for the same tags
        final TagListModel model = new TagListModel();

        assertThat(model.wrap("alpha"))
                .isEqualTo(model.wrap("alpha"));
        assertThat(model.wrap("alpha"))
                .isNotEqualTo(model.wrap("beta"));
    }

    private static List<String> labels(final TagListModel model) {
        final List<String> labels = new ArrayList<>();
        model.onRangeChange(null, null, false, new PageRequest(0, 100), page -> page.getValues()
                .forEach(item -> labels.add(item.getLabel())));
        return labels;
    }

    private static List<String> tags(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "tag-" + i)
                .collect(Collectors.toList());
    }
}
