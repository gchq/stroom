/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.store.offheapstore.serdes;

import org.junit.Test;
import stroom.refdata.store.FastInfosetValue;
import stroom.refdata.store.StringValue;
import stroom.refdata.store.offheapstore.ValueStoreMeta;

public class TestValueStoreMetaSerde extends AbstractSerdeTest<ValueStoreMeta, ValueStoreMetaSerde> {

    @Test
    public void testSerializeDeserialize() {
        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(FastInfosetValue.TYPE_ID, 123);

        doSerialisationDeserialisationTest(valueStoreMeta, ValueStoreMetaSerde::new);
    }

    @Test
    public void testExtractTypeId() {

        ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        doExtractionTest(valueStoreMeta, getSerde()::extractTypeId, ValueStoreMeta::getTypeId);
    }

    @Test
    public void testExtractReferenceCount() {

        ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        doExtractionTest(valueStoreMeta, getSerde()::extractReferenceCount, ValueStoreMeta::getReferenceCount);
    }

    @Override
    Class<ValueStoreMetaSerde> getSerdeType() {
        return ValueStoreMetaSerde.class;
    }
}