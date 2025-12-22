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

package stroom.util.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPrimitiveValueConverterMapImpl {

    static final PrimitiveValueConverter<MyEnum> CONVERTER = new PrimitiveValueConverterMapImpl<>(
            MyEnum.class, MyEnum.values());

    @Test
    void testAsPrimitiveByte() {

        for (final MyEnum myEnum : MyEnum.values()) {
            final byte primitiveValue = myEnum.getPrimitiveValue();

            final MyEnum myEnum2 = CONVERTER.fromPrimitiveValue(primitiveValue);

            assertThat(myEnum2)
                    .isEqualTo(myEnum);
        }

        assertThat(CONVERTER.fromPrimitiveValue((byte) -1))
                .isNull();
        assertThat(CONVERTER.fromPrimitiveValue((byte) 3))
                .isNull();
        assertThat(CONVERTER.fromPrimitiveValue((byte) 999))
                .isNull();
    }

    @Test
    void testAsBoxedByte() {
        for (final MyEnum myEnum : MyEnum.values()) {
            final Byte primitiveValue = myEnum.getPrimitiveValue();

            final MyEnum myEnum2 = CONVERTER.fromPrimitiveValue(primitiveValue);

            assertThat(myEnum2)
                    .isEqualTo(myEnum);
        }

        assertThat(CONVERTER.fromPrimitiveValue(Byte.valueOf((byte) -1)))
                .isNull();
        assertThat(CONVERTER.fromPrimitiveValue(Byte.valueOf((byte) 3)))
                .isNull();
        assertThat(CONVERTER.fromPrimitiveValue(Byte.valueOf((byte) 999)))
                .isNull();
    }

    @Test
    void testAsBoxedByteWithDefault() {

        assertThat(CONVERTER.fromPrimitiveValue(null, MyEnum.DOG))
                .isEqualTo(MyEnum.DOG);
        assertThat(CONVERTER.fromPrimitiveValue((byte) -1, MyEnum.DOG))
                .isEqualTo(null);
        assertThat(CONVERTER.fromPrimitiveValue((byte) 3, MyEnum.DOG))
                .isEqualTo(null);
        assertThat(CONVERTER.fromPrimitiveValue((byte) 999, MyEnum.DOG))
                .isEqualTo(null);
    }


    // --------------------------------------------------------------------------------


    private enum MyEnum implements HasPrimitiveValue {
        CAT((byte) 5),
        HAMSTER((byte) 10),
        GERBIL((byte) 15),
        RAT((byte) 20),
        DOG((byte) 25),
        ;

        private final byte primitiveValue;

        MyEnum(final byte primitiveValue) {
            this.primitiveValue = primitiveValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }
    }
}
