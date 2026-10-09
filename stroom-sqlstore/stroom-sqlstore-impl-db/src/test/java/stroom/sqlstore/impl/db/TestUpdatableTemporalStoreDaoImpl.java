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

package stroom.sqlstore.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestUpdatableTemporalStoreDaoImpl {

    @Mock
    private SqlStoreDbConnProvider sqlStoreDbConnProvider;

    @Mock
    private ExpressionMapperFactory expressionMapperFactory;

    @Mock
    private ExpressionMapper expressionMapper;

    private UpdatableTemporalStoreDaoImpl dao;

    @BeforeEach
    void setUp() {
        Mockito.when(expressionMapperFactory.create()).thenReturn(expressionMapper);
        dao = new UpdatableTemporalStoreDaoImpl(sqlStoreDbConnProvider, expressionMapperFactory);
    }

    private static final String UUID = "11111111-2222-3333-4444-555555555555";

    @Test
    void testCreateWithNullDocUuid() {
        final TemporalEntry entry = new TemporalEntry("map1", "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create(null, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testCreateWithBlankDocUuid() {
        final TemporalEntry entry = new TemporalEntry("map1", "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create("  ", entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testCreateWithNullEntry() {
        assertThatThrownBy(() -> dao.create(UUID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Entry cannot be null.");
    }

    @Test
    void testCreateWithNullMap() {
        final TemporalEntry entry = new TemporalEntry(null, "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Map name must be defined and not empty.");
    }

    @Test
    void testCreateWithEmptyMap() {
        final TemporalEntry entry = new TemporalEntry("  ", "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Map name must be defined and not empty.");
    }

    @Test
    void testCreateWithNullKey() {
        final TemporalEntry entry = new TemporalEntry("map1", null, 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Key must be defined and not empty.");
    }

    @Test
    void testCreateWithEmptyKey() {
        final TemporalEntry entry = new TemporalEntry("map1", "", 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Key must be defined and not empty.");
    }

    @Test
    void testCreateWithNullEffectiveTime() {
        final TemporalEntry entry = new TemporalEntry("map1", "key1", null, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Effective time must be defined.");
    }

    @Test
    void testFetchWithNullDocUuid() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", 1000L);
        assertThatThrownBy(() -> dao.fetch(null, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testFetchWithNullId() {
        assertThatThrownBy(() -> dao.fetch(UUID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Id cannot be null.");
    }

    /**
     * A null map name on an id is no longer an error. Scoping is by document UUID, so the map
     * name on an id is not consulted at all - see UpdatableTemporalStoreDaoImpl's class doc.
     */
    @Test
    void testFetchWithNullMapIsAccepted() {
        final TemporalEntryId id = new TemporalEntryId(null, "key1", 1000L);
        assertThatThrownBy(() -> dao.fetch(UUID, id))
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFetchWithNullKey() {
        final TemporalEntryId id = new TemporalEntryId("map1", null, 1000L);
        assertThatThrownBy(() -> dao.fetch(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Key must be defined and not empty.");
    }

    @Test
    void testFetchWithNullEffectiveTime() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", null);
        assertThatThrownBy(() -> dao.fetch(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Effective time must be defined.");
    }

    @Test
    void testDeleteWithNullDocUuid() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", 1000L);
        assertThatThrownBy(() -> dao.delete(null, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testDeleteWithNullId() {
        assertThatThrownBy(() -> dao.delete(UUID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Id cannot be null.");
    }

    @Test
    void testDeleteWithNullKey() {
        final TemporalEntryId id = new TemporalEntryId("map1", null, 1000L);
        assertThatThrownBy(() -> dao.delete(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Key must be defined and not empty.");
    }

    @Test
    void testDeleteWithNullEffectiveTime() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", null);
        assertThatThrownBy(() -> dao.delete(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Effective time must be defined.");
    }

    @Test
    void testClearWithNullDocUuid() {
        assertThatThrownBy(() -> dao.clear(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testFetchAllWithNullDocUuid() {
        assertThatThrownBy(() -> dao.fetchAll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testGetTimeRangeWithNullDocUuid() {
        assertThatThrownBy(() -> dao.getTimeRange(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }
}
