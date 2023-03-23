package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

public class ValHasher {

    public static long hash(final Val[] values) {
        if (values == null) {
            return -1;
        } else if (values.length == 0) {
            return 0;
        }

        Hasher hasher = Hashing.sipHash24().newHasher();
        for (final Val val : values) {
            hasher = hasher.putByte(val.type().getId());
            switch (val.type()) {
                case NULL -> hasher = hasher.putBoolean(false);
                case BOOLEAN -> hasher = hasher.putBoolean(val.toBoolean());
                case FLOAT, DOUBLE -> hasher.putDouble(val.toDouble());
                case INTEGER, LONG, DATE -> hasher.putLong(val.toLong());
                case STRING, ERR -> hasher.putString(val.toString(), StandardCharsets.UTF_8);
                default -> throw new IllegalStateException("Unexpected value: " + val.type());
            }
        }
        return hasher.hash().asLong();
    }
}
