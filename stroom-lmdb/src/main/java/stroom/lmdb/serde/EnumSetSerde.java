package stroom.lmdb.serde;

import stroom.lmdb.serde.EnumSetSerde.HasBitPosition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link Serde} for (de)serialising an {@link EnumSet}.
 * The {@link Enum} must implement {@link HasBitPosition}.
 * (De)Serialisation is fixed width, using as many bytes as is required for
 * all the possible items in the set, i.e. one byte per eight items.
 *
 * @param <T>
 */
public class EnumSetSerde<T extends Enum<T> & HasBitPosition>
        implements Serde<EnumSet<T>> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EnumSetSerde.class);

    // TODO we could make a variable length version, but how likely are we to need to support
    //  an enum with tens of values.

    /**
     * The number of bytes used to serialise this
     */
    private final int sizeInBytes;
    private final int bitCount;
    private final Class<T> type;
    /**
     * Array of all enum constants indexed by the value of HasBitPosition::getBitPosition.
     * Allows us to map from a bit-position back to an enum quickly.
     * Might be sparse if the enum has gaps in its bit positions.
     */
    private final T[] sparseArr;

    /**
     * Create a {@link Serde} for (de)serialising an {@link EnumSet} of T.
     * The {@link Enum} must implement {@link HasBitPosition}.
     *
     * @param type The type of the {@link Enum}.
     */
    public EnumSetSerde(final Class<T> type) {
        Objects.requireNonNull(type);
        this.type = type;
        final T[] enumConstants = type.getEnumConstants();
        this.bitCount = enumConstants.length;
        final int byteCount = bitCount / 8;
        this.sizeInBytes = bitCount % 8 == 0
                ? byteCount
                : byteCount + 1;

        final Set<Integer> bitPositions = new HashSet<>(bitCount);
        final int maxBitPosition = Arrays.stream(enumConstants)
                .mapToInt(HasBitPosition::getBitPosition)
                .max()
                .orElseThrow(() -> new IllegalArgumentException("No items in enum " + type.getName()));

        //noinspection unchecked
        sparseArr = (T[]) Array.newInstance(type, maxBitPosition + 1);

        for (final T enumConstant : enumConstants) {
            final int bitPosition = enumConstant.getBitPosition();
            if (bitPosition < 0) {
                throw new IllegalArgumentException(LogUtil.message(
                        "bitPosition {} in enum {} must be >= 0.", bitPosition, type.getName()));
            }
            final boolean success = bitPositions.add(bitPosition);
            if (!success) {
                // Already there, so a dup
                throw new IllegalArgumentException(LogUtil.message(
                        "Duplicate bitPosition {} in enum {}. " +
                        "Bit positions must all be unique.", bitPosition, type.getName()));
            }
            sparseArr[enumConstant.getBitPosition()] = enumConstant;
        }
    }

    /**
     * Create a {@link Serde} for (de)serialising an {@link EnumSet} of T.
     * The {@link Enum} must implement {@link HasBitPosition}.
     *
     * @param type The type of the {@link Enum}.
     */
    public EnumSetSerde<T> forClass(final Class<T> type) {
        return new EnumSetSerde<>(type);
    }

    @Override
    public EnumSet<T> deserialize(final ByteBuffer byteBuffer) {
        final EnumSet<T> enumSet = EnumSet.noneOf(type);

        int bitIdx = 0;
        for (int byteIdx = 0; byteIdx < sizeInBytes && bitIdx < bitCount; byteIdx++) {
            final int aByte = byteBuffer.get();
            if (aByte != 0) {
                for (int bitIdxInByte = 0; bitIdxInByte < 8 && bitIdx < bitCount; bitIdxInByte++) {
                    final boolean isSet = (aByte & (1 << bitIdxInByte)) > 0;
//                LOGGER.info("byteIdx: {}, bitIdx: {}, bitIdxInByte: {}, isSet: {}",
//                        byteIdx, bitIdx, bitIdxInByte, isSet);
                    if (isSet) {
                        final T anEnum = sparseArr[bitIdx];
                        if (anEnum == null) {
                            throw new IllegalStateException(LogUtil.message(
                                    "No enum of type {} exists with bit-position {}", type.getName(), bitIdx));
                        }
                        enumSet.add(anEnum);
                    }
                    bitIdx++;
                }
            } else {
                // Skipped a whole byte that had no bits set, so move the bitIdx on by 8
                bitIdx += 8;
            }
        }
        byteBuffer.flip();
        return enumSet;
    }

    // TODO delete this. Assuming that byteBuffer.put/get is better than creating a the
    //  boolean[]
//    @Override
//    public void serialize(final ByteBuffer byteBuffer, final EnumSet<T> enumSet) {
//        Objects.requireNonNull(enumSet);
//        if (enumSet.isEmpty()) {
//            // Saves creating the BitSet
//            for (int i = 0; i < sizeInBytes; i++) {
//                byteBuffer.put((byte) 0);
//            }
//        } else {
//            final boolean[] allBits = new boolean[bitCount];
//            for (final T anEnum : enumSet) {
//                allBits[anEnum.getBitPosition()] = true;
//            }
//
//            int bitIdx = 0;
//            for (int byteIdx = 0; byteIdx < sizeInBytes && bitIdx < bitCount; byteIdx++) {
//                int aByte = 0;
//                for (int bitIdxInByte = 0; bitIdxInByte < 8 && bitIdx < bitCount; bitIdxInByte++) {
//                    if (allBits[bitIdx]) {
//                        aByte = aByte | (1 << bitIdxInByte);
//                    }
//                    bitIdx++;
//                }
//                byteBuffer.put((byte) aByte);
//            }
//        }
//        byteBuffer.flip();
//    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final EnumSet<T> enumSet) {
        Objects.requireNonNull(enumSet);
        // Clear all bytes and advance the byteBuffer position to the end of our enumSet bytes
        for (int i = 0; i < sizeInBytes; i++) {
            byteBuffer.put((byte) 0);
        }
        for (final T anEnum : enumSet) {
            final int bitPosition = anEnum.getBitPosition();
            // Which byte this bit sits in, e.g. bitIdx 2 is in byteIdx 0, bitIdx 10 is in byteIdx 1
            final int byteIdx = bitPosition / 8;
            // The index of the bit within the targeted byte
            final int bitIdxInByte = bitPosition % 8;
            // Set the bit at bitIdxInByte
            final int aByte = byteBuffer.get(byteIdx) | (1 << bitIdxInByte);
            byteBuffer.put(byteIdx, (byte) aByte);
        }
        byteBuffer.flip();
    }

    @Override
    public int getBufferCapacity() {
        return sizeInBytes;
    }

    public Class<T> getType() {
        return type;
    }


    // --------------------------------------------------------------------------------


    public interface HasBitPosition {

        /**
         * @return The unique bit position (>= 0) for this object, e.g. 0, 1, 2, 3, etc.
         */
        int getBitPosition();
    }
}
