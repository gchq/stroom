package stroom.bytebuffer;

public class PowTwoUtils {

    static final int MAX_BUFFER_CAPACITY = 65_536;
    private static final double LOG2 = Math.log(2);

    private PowTwoUtils() {
    }

    public static int getOffset(final int minCapacity) {
        if (minCapacity <= 4) {
            return 2;
        } else if (minCapacity <= 8) {
            return 3;
        } else if (minCapacity <= 16) {
            return 4;
        } else if (minCapacity <= 32) {
            return 5;
        } else if (minCapacity <= 64) {
            return 6;
        } else if (minCapacity <= 128) {
            return 7;
        } else if (minCapacity <= 256) {
            return 8;
        } else if (minCapacity <= 512) {
            return 9;
        } else if (minCapacity <= 1_024) {
            return 10;
        } else if (minCapacity <= 2_048) {
            return 11;
        } else if (minCapacity <= 4_096) {
            return 12;
        } else if (minCapacity <= 8_192) {
            return 13;
        } else if (minCapacity <= 16_384) {
            return 14;
        } else if (minCapacity <= 32_768) {
            return 15;
        } else if (minCapacity <= 65_536) {
            return 16;
        } else {
            return (int) Math.ceil(log2(minCapacity));
        }
    }

    static Integer getOffsetExact(Integer capacity) {
        if (capacity == null) {
            return null;
        } else {
            return switch (capacity) {
                case 4 -> 2;
                case 8 -> 3;
                case 16 -> 4;
                case 32 -> 5;
                case 64 -> 6;
                case 128 -> 7;
                case 256 -> 8;
                case 512 -> 9;
                case 1_024 -> 10;
                case 2_048 -> 11;
                case 4_096 -> 12;
                case 8_192 -> 13;
                case 16_384 -> 14;
                case 32_768 -> 15;
                case MAX_BUFFER_CAPACITY -> 16;
                default -> null;
            };
        }
    }

    private static double log2(int n) {
        return Math.log(n) / LOG2;
    }
}
