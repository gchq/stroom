/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.StagingRefDataValue;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled  // TODO: 21/04/2023 Delete class
class TestStagingRefDataValueSerde extends AbstractSerdeTest<StagingRefDataValue, StagingRefDataValueSerde> {

    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();

    @Test
    void testSerialisationDeserialisation_fastInfoSet() {

        final FastInfosetValue fastInfosetValue = new FastInfosetValue(ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4}));
        final StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(fastInfosetValue);
        final StagingRefDataValue stagingRefDataValue2 = doSerialisationDeserialisationTest(stagingRefDataValue);

        assertThat(stagingRefDataValue2.getTypeId())
                .isEqualTo(FastInfosetValue.TYPE_ID);
        assertThat(stagingRefDataValue2)
                .extracting(StagingRefDataValue::getRefDataValue)
                .isInstanceOf(FastInfosetValue.class);

        final FastInfosetValue fastInfosetValue2 = (FastInfosetValue) stagingRefDataValue2.getRefDataValue();

        assertThat(fastInfosetValue.getByteBuffer()).isEqualTo(fastInfosetValue2.getByteBuffer());

        // Pass in null to getValueHashCode, so we know the hash is a stored one, rather than generated on the fly
        assertThat(stagingRefDataValue2)
                .extracting(val ->
                        val.getRefDataValue().getValueHashCode(null))
                .isEqualTo(stagingRefDataValue.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Test
    void testSerialisationDeserialisation_string() {

        final StringValue stringValue = new StringValue("foo");
        final StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(stringValue);
        final StagingRefDataValue stagingRefDataValue2 = doSerialisationDeserialisationTest(stagingRefDataValue);

        assertThat(stagingRefDataValue2.getTypeId())
                .isEqualTo(StringValue.TYPE_ID);
        assertThat(stagingRefDataValue2)
                .extracting(StagingRefDataValue::getRefDataValue)
                .isInstanceOf(StringValue.class);

        final StringValue stringValue2 = (StringValue) stagingRefDataValue2.getRefDataValue();
        assertThat(stringValue2.getValue())
                .isEqualTo("foo");

        // Pass in null to getValueHashCode, so we know the hash is a stored one, rather than generated on the fly
        assertThat(stagingRefDataValue2)
                .extracting(val ->
                        val.getRefDataValue().getValueHashCode(null))
                .isEqualTo(stagingRefDataValue.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Test
    void testSerialisationDeserialisation_null() {

        final NullValue nullValue = NullValue.getInstance();
        final StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(nullValue);
        final StagingRefDataValue stagingRefDataValue2 = doSerialisationDeserialisationTest(stagingRefDataValue);

        assertThat(stagingRefDataValue2.getTypeId())
                .isEqualTo(NullValue.TYPE_ID);
        assertThat(stagingRefDataValue2.getRefDataValue())
                .isInstanceOf(NullValue.class);
        assertThat(stagingRefDataValue2.getRefDataValue().isNullValue())
                .isTrue();

        // Pass in null to getValueHashCode, so we know the hash is a stored one, rather than generated on the fly
        assertThat(stagingRefDataValue2)
                .extracting(val ->
                        val.getRefDataValue().getValueHashCode(null))
                .isEqualTo(stagingRefDataValue.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Test
    void testTypeIdExtraction() {
        final StringValue stringValue = new StringValue("foo");
        final StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(stringValue);
        doExtractionTest(stagingRefDataValue, getSerde()::extractTypeId, StagingRefDataValue::getTypeId);
    }

    @Test
    void testValueHashExtraction() {
        final StringValue stringValue = new StringValue("foo");
        final StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(stringValue);
        doExtractionTest(
                stagingRefDataValue,
                getSerde()::extractValueHash,
                val -> val.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Override
    TypeLiteral<StagingRefDataValueSerde> getSerdeType() {
        return new TypeLiteral<StagingRefDataValueSerde>(){};
    }

    @Override
    Supplier<StagingRefDataValueSerde> getSerdeSupplier() {
        return () -> new StagingRefDataValueSerde(
                new GenericRefDataValueSerde(new RefDataValueSerdeFactory()),
                valueStoreHashAlgorithm);
    }
}
