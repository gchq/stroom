package stroom.lmdb.serde;

import stroom.bytebuffer.ByteArrayUtils;
import stroom.lmdb.serde.EnumSetSerde.HasBitPosition;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestEnumSetSerde {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEnumSetSerde.class);

    @TestFactory
    Stream<DynamicTest> testSerDeser() {
        // We only need 2 bytes, but use a buffer of 3 for a better test
        final ByteBuffer byteBuffer = ByteBuffer.allocate(3);
        final EnumSetSerde<Feature> serde = new EnumSetSerde<>(Feature.class);
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<EnumSet<Feature>>() {
                })
                .withWrappedOutputType(new TypeLiteral<EnumSet<Feature>>() {
                })
                .withTestFunction(testCase -> {
                    byteBuffer.clear();
                    serde.serialize(byteBuffer, testCase.getInput());


                    final byte[] arr = byteBuffer.array();
                    final List<String> parts = new ArrayList<>();
                    for (final byte aByte : arr) {
                        final String part = Strings.padStart(Integer.toBinaryString(aByte), 8, '0');
                        parts.add(part);
                    }

                    LOGGER.info("len: {}, arr: {}, bits: [{}]",
                            arr.length, ByteArrayUtils.byteArrayToHex(arr), String.join("|", parts));

                    return serde.deserialize(byteBuffer);
                })
                .withSimpleEqualityAssertion()
                .addCase(EnumSet.noneOf(Feature.class),
                        EnumSet.noneOf(Feature.class))
                .addCase(EnumSet.allOf(Feature.class),
                        EnumSet.allOf(Feature.class))
                .addCase(EnumSet.of(Feature.AIR_CON),
                        EnumSet.of(Feature.AIR_CON))
                .addCase(EnumSet.of(Feature.DIFF_LOCK),
                        EnumSet.of(Feature.DIFF_LOCK))
                .addCase(EnumSet.of(Feature.DASH_CAM),
                        EnumSet.of(Feature.DASH_CAM))
                .addCase(EnumSet.of(Feature.SAT_NAV, Feature.DIFF_LOCK),
                        EnumSet.of(Feature.SAT_NAV, Feature.DIFF_LOCK))
                .addCase(EnumSet.of(Feature.ISO_FIX, Feature.FOUR_WHEEL_DRIVE, Feature.LOW_RANGE, Feature.DASH_CAM),
                        EnumSet.of(Feature.ISO_FIX, Feature.FOUR_WHEEL_DRIVE, Feature.LOW_RANGE, Feature.DASH_CAM))
                .build();
    }


    // --------------------------------------------------------------------------------


    private enum Feature implements HasBitPosition {

        ISO_FIX(0),
        AIR_CON(1),
        SAT_NAV(2),
        SUN_ROOF(3),
        HEATED_SEATS(4),
        FM_RADIO(5),
        DAB_RADIO(6),
        FOUR_WHEEL_DRIVE(7),
        LOW_RANGE(8),
        DIFF_LOCK(9),
        DASH_CAM(10),
        ;

        private final int bitPosition;
        private static final Map<Integer, Feature> BIT_POS_TO_FEATURE_MAP = Arrays.stream(Feature.values())
                .collect(Collectors.toMap(Feature::getBitPosition, Function.identity()));

        Feature(final int bitPosition) {
            this.bitPosition = bitPosition;
        }

        @Override
        public int getBitPosition() {
            return bitPosition;
        }

        public static Feature fromBitPosition(final int bitPosition) {
            return Objects.requireNonNull(BIT_POS_TO_FEATURE_MAP.get(bitPosition));
        }
    }
}
