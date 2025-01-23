package stroom.planb.impl.data;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import java.io.IOException;
import java.nio.file.Path;

public class FileHashUtil {

    public static String hash(final Path path) throws IOException {
        final HashCode hc = com.google.common.io.Files.asByteSource(path.toFile()).hash(Hashing.murmur3_128());
        return hc.toString();
    }
}
