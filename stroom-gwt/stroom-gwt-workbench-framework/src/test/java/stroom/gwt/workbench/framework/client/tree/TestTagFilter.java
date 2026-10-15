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

package stroom.gwt.workbench.framework.client.tree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestTagFilter {

    @Test
    void testNoFilterMatchesEverything() {
        final TagFilter filter = new TagFilter();
        assertThat(filter.getActiveCount()).isZero();
        assertThat(filter.matches(Set.of())).isTrue();
        assertThat(filter.matches(Set.of("a"))).isTrue();
    }

    @Test
    void testIncludedTagsMatchAny() {
        final TagFilter filter = new TagFilter();
        filter.toggleChecked("a");
        filter.toggleChecked("b");
        assertThat(filter.matches(Set.of("a"))).isTrue();
        assertThat(filter.matches(Set.of("b", "c"))).isTrue();
        assertThat(filter.matches(Set.of("c"))).isFalse();
        assertThat(filter.matches(Set.of())).isFalse();
        assertThat(filter.getIncluded()).containsExactly("a", "b");
    }

    @Test
    void testExcludedTagsWinOverIncluded() {
        final TagFilter filter = new TagFilter();
        filter.toggleChecked("a");
        filter.toggleExcluded("b");
        assertThat(filter.matches(Set.of("a"))).isTrue();
        assertThat(filter.matches(Set.of("a", "b"))).isFalse();
        assertThat(filter.matches(Set.of("c"))).isFalse();
    }

    @Test
    void testExcludeOnly() {
        final TagFilter filter = new TagFilter();
        filter.toggleExcluded(TagFilter.PLAY);
        assertThat(filter.matches(Set.of())).isTrue();
        assertThat(filter.matches(Set.of(TagFilter.PLAY))).isFalse();
    }

    @Test
    void testToggling() {
        final TagFilter filter = new TagFilter();
        filter.toggleChecked("a");
        assertThat(filter.isIncluded("a")).isTrue();
        // Excluding an included tag stops it being included
        filter.toggleExcluded("a");
        assertThat(filter.isIncluded("a")).isFalse();
        assertThat(filter.isExcluded("a")).isTrue();
        assertThat(filter.getActiveCount()).isEqualTo(1);
        // Including an excluded tag
        filter.toggleExcluded("a");
        assertThat(filter.isExcluded("a")).isFalse();
        assertThat(filter.isIncluded("a")).isTrue();
        filter.toggleChecked("a");
        assertThat(filter.getActiveCount()).isZero();
        // Unchecking an excluded tag stops it being filtered on
        filter.toggleExcluded("b");
        filter.toggleChecked("b");
        assertThat(filter.isExcluded("b")).isFalse();
        assertThat(filter.isIncluded("b")).isFalse();
    }

    @Test
    void testIncludeAllAndClear() {
        final TagFilter filter = new TagFilter();
        filter.toggleExcluded("x");
        filter.includeAll(List.of("a", "b"));
        assertThat(filter.isExcluded("x")).isFalse();
        assertThat(filter.getIncluded()).containsExactly("a", "b");
        filter.clear();
        assertThat(filter.getActiveCount()).isZero();
    }

    @Test
    void testIsStatusTag() {
        assertThat(TagFilter.isStatusTag(TagFilter.NEW)).isTrue();
        assertThat(TagFilter.isStatusTag(TagFilter.MODIFIED)).isTrue();
        assertThat(TagFilter.isStatusTag(TagFilter.RELATED)).isTrue();
        assertThat(TagFilter.isStatusTag(TagFilter.PLAY)).isFalse();
        assertThat(TagFilter.isStatusTag("experimental")).isFalse();
    }

    @Test
    void testIsCustomTag() {
        assertThat(TagFilter.isCustomTag("experimental")).isTrue();
        assertThat(TagFilter.isCustomTag(TagFilter.PLAY)).isFalse();
        assertThat(TagFilter.isCustomTag("dev")).isFalse();
        assertThat(TagFilter.isCustomTag("autodocs")).isFalse();
        assertThat(TagFilter.isCustomTag(TagFilter.NEW)).isFalse();
        assertThat(TagFilter.isCustomTag(TagFilter.MODIFIED)).isFalse();
        assertThat(TagFilter.isCustomTag(TagFilter.RELATED)).isFalse();
    }
}
