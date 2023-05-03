/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.TableSettings;

import org.junit.jupiter.api.Test;

import java.util.Collections;

class TestMapDataStore extends AbstractDataStoreTest {

    @Override
    DataStore create(final QueryKey queryKey,
                     final String componentId,
                     final TableSettings tableSettings,
                     final AbstractResultStoreConfig resultStoreConfig,
                     final DataStoreSettings dataStoreSettings) {
        final FieldIndex fieldIndex = new FieldIndex();
        final Serialisers serialisers = new Serialisers(new SearchResultStoreConfig());
        return new MapDataStore(
                serialisers,
                tableSettings,
                fieldIndex,
                Collections.emptyMap(),
                dataStoreSettings);
    }

    @Test
    void basicTest() {
        super.basicTest();
    }

    @Test
    void sortedTextTest() {
        super.sortedTextTest();
    }

    @Test
    void sortedNumberTest() {
        super.sortedNumberTest();
    }

    @Test
    void sortedCountedTextTest1() {
        super.sortedCountedTextTest1();
    }

    @Test
    void sortedCountedTextTest2() {
        super.sortedCountedTextTest2();
    }

    @Test
    void sortedCountedTextTest3() {
        super.sortedCountedTextTest3();
    }
}
