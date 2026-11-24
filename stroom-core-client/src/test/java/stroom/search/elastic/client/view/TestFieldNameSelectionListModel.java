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

package stroom.search.elastic.client.view;

import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldNameSelectionListModel {

    private static final String NONE = "None";

    private FieldNameSelectionListModel model;

    @BeforeEach
    void setUp() {
        model = new FieldNameSelectionListModel();
        model.setNonSelectString(NONE);
    }

    @Test
    void testNoFilter_listsNoneThenEveryField() {
        model.addItems(List.of("message", "host.name"));

        assertThat(labels(page(null, false, 0, 100).getValues())).containsExactly(NONE, "message", "host.name");
    }

    @Test
    void testFilter_ignoresCaseAndListsPrefixMatchesFirst() {
        model.addItems(List.of("event.message", "Message", "host.name", "message.keyword"));

        // 'None' is never a match for typed text
        assertThat(labels(page("MESS", false, 0, 100).getValues()))
                .containsExactly("Message", "message.keyword", "event.message");
    }

    @Test
    void testFilter_noMatches() {
        model.addItems(List.of("message"));

        assertThat(page("zzz", false, 0, 100).getValues()).isEmpty();
    }

    @Test
    void testPaging_returnsTheRequestedPageAndTheTotal() {
        model.addItems(fieldNames(250));

        final ResultPage<SimpleSelectionItemWrapper<String>> page = page(null, false, 100, 100);

        // 250 fields plus 'None'
        assertThat(page.getPageSize()).isEqualTo(251);
        assertThat(page.getPageStart()).isEqualTo(100);
        assertThat(labels(page.getValues())).hasSize(100).first().isEqualTo("field099");
    }

    @Test
    void testPaging_newFilterStartsFromTheFirstPage() {
        model.addItems(fieldNames(250));

        final ResultPage<SimpleSelectionItemWrapper<String>> page = page("field1", true, 200, 100);

        assertThat(page.getPageStart()).isZero();
        assertThat(labels(page.getValues())).hasSize(100).first().isEqualTo("field100");
    }

    @Test
    void testPaging_pagePastTheEndFallsBackToTheFirstPage() {
        model.addItems(fieldNames(250));

        final ResultPage<SimpleSelectionItemWrapper<String>> page = page("field00", false, 200, 100);

        assertThat(page.getPageStart()).isZero();
        assertThat(labels(page.getValues())).hasSize(10);
    }

    @Test
    void testAlwaysFilterableAndPaged() {
        assertThat(model.displayFilter()).isTrue();
        assertThat(model.displayPager()).isTrue();
    }

    private ResultPage<SimpleSelectionItemWrapper<String>> page(final String filter,
                                                                final boolean filterChange,
                                                                final int offset,
                                                                final int length) {
        final AtomicReference<ResultPage<SimpleSelectionItemWrapper<String>>> result = new AtomicReference<>();
        model.onRangeChange(null, filter, filterChange, new PageRequest(offset, length), result::set);
        return result.get();
    }

    private static List<String> labels(final List<SimpleSelectionItemWrapper<String>> items) {
        final List<String> labels = new ArrayList<>();
        items.forEach(item -> labels.add(item.getLabel()));
        return labels;
    }

    private static List<String> fieldNames(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> String.format("field%03d", i))
                .toList();
    }
}
