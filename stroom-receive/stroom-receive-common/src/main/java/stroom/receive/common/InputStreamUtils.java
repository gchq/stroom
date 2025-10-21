package stroom.receive.common;

import stroom.util.io.ByteSize;

import org.apache.commons.io.input.BoundedInputStream;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamUtils {

    private InputStreamUtils() {}

    public static BoundedInputStream getBoundedInputStream(final InputStream inputStream, final ByteSize maxSize)
            throws IOException {
        return BoundedInputStream.builder()
                .setInputStream(inputStream)
                .setMaxCount(maxSize == null ? -1 : maxSize.getBytes())
                .setOnMaxCount((max, count) -> {
                    throw new ContentTooLargeException("Maximum request size exceeded (" + ByteSize.ofBytes(max) + ")");
                })
                .get();
    }

}
