package stroom.util.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPrimitiveValueConverterArrayImpl {

    private static final PrimitiveValueConverter<MyEnum> CONVERTER = new PrimitiveValueConverterArrayImpl<>(
            MyEnum.class, MyEnum.values());

    @Test
    void testAsPrimitiveByte() {

        // Test all enum values
        for (final MyEnum myEnum : MyEnum.values()) {
            final byte primitiveValue = myEnum.getPrimitiveValue();

            final MyEnum myEnum2 = CONVERTER.fromPrimitiveValue(primitiveValue);

            assertThat(myEnum2)
                    .isEqualTo(myEnum);
        }

        // Out of bounds
        assertThat(CONVERTER.fromPrimitiveValue((byte) -1))
                .isNull();
        // Unknown primitiveValue
        assertThat(CONVERTER.fromPrimitiveValue((byte) 3))
                .isNull();
        // Out of bounds
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

        // Out of bounds
        assertThat(CONVERTER.fromPrimitiveValue(Byte.valueOf((byte) -1)))
                .isNull();
        // Unknown primitiveValue
        assertThat(CONVERTER.fromPrimitiveValue(Byte.valueOf((byte) 3)))
                .isNull();
        // Out of bounds
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
