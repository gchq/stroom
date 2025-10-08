package stroom.lmdb.stream;

import java.nio.ByteBuffer;
import java.util.Comparator;

import static java.util.Objects.requireNonNull;

class OldUnsignedByteBufferComparator implements Comparator<ByteBuffer> {

    @Override
    public int compare(final ByteBuffer o1, final ByteBuffer o2) {
        requireNonNull(o1);
        requireNonNull(o2);
        if (o1.equals(o2)) {
            return 0;
        }
        final int minLength = Math.min(o1.limit(), o2.limit());
        final int minWords = minLength / Long.BYTES;

//        final boolean reverse1 = o1.order() == LITTLE_ENDIAN;
//        final boolean reverse2 = o2.order() == LITTLE_ENDIAN;
//        for (int i = 0; i < minWords * Long.BYTES; i += Long.BYTES) {
//            final long lw = reverse1 ? Long.reverseBytes(o1.getLong(i)) : o1.getLong(i);
//            final long rw = reverse2 ? Long.reverseBytes(o2.getLong(i)) : o2.getLong(i);
//            final int diff = Long.compareUnsigned(lw, rw);
//            if (diff != 0) {
//                return diff;
//            }
//        }

        for (int i = 0; i < minWords * Long.BYTES; i += Long.BYTES) {
            final long lw = o1.getLong(i);
            final long rw = o2.getLong(i);
            final int diff = Long.compareUnsigned(lw, rw);
            if (diff != 0) {
                return diff;
            }
        }

        for (int i = minWords * Long.BYTES; i < minLength; i++) {
            final int lw = Byte.toUnsignedInt(o1.get(i));
            final int rw = Byte.toUnsignedInt(o2.get(i));
            final int result = Integer.compareUnsigned(lw, rw);
            if (result != 0) {
                return result;
            }
        }

        return o1.remaining() - o2.remaining();
    }
}
