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

package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNodeKey;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestOpenItemsImpl {

    private static final ExplorerNodeKey SYSTEM_KEY = ExplorerConstants.SYSTEM_NODE.getUniqueKey();
    private static final ExplorerNodeKey FAVOURITES_KEY = ExplorerConstants.FAVOURITES_NODE.getUniqueKey();

    // gh-5843: the client omits null sets (e.g. ensureVisible) from the request, so create() must cope with null
    @Test
    void testCreateWithNull() {
        final OpenItems openItems = OpenItemsImpl.create(null);

        assertThat(openItems.isOpen(SYSTEM_KEY)).isFalse();
        assertThat(openItems.isForcedOpen(SYSTEM_KEY)).isFalse();
        assertThat(openItems.toString()).isEmpty();
    }

    @Test
    void testCreateWithNullIsMutable() {
        final OpenItems openItems = OpenItemsImpl.create(null);

        openItems.addAll(List.of(SYSTEM_KEY));

        assertThat(openItems.isOpen(SYSTEM_KEY)).isTrue();
    }

    @Test
    void testCreate() {
        final OpenItems openItems = OpenItemsImpl.create(List.of(SYSTEM_KEY));

        assertThat(openItems.isOpen(SYSTEM_KEY)).isTrue();
        assertThat(openItems.isOpen(FAVOURITES_KEY)).isFalse();
        assertThat(openItems.isForcedOpen(SYSTEM_KEY)).isFalse();
    }

    @Test
    void testCreateCopiesInput() {
        final Set<ExplorerNodeKey> input = new HashSet<>();
        input.add(SYSTEM_KEY);
        final OpenItems openItems = OpenItemsImpl.create(input);

        openItems.addAll(List.of(FAVOURITES_KEY));

        assertThat(openItems.isOpen(FAVOURITES_KEY)).isTrue();
        assertThat(input).containsExactly(SYSTEM_KEY);
    }

    @Test
    void testCreateWithForcedWithNulls() {
        final OpenItems openItems = OpenItemsImpl.createWithForced(null, null);

        assertThat(openItems.isOpen(SYSTEM_KEY)).isFalse();
        assertThat(openItems.isForcedOpen(SYSTEM_KEY)).isFalse();
    }
}
