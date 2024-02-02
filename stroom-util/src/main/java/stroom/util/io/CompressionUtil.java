package stroom.util.io;

public final class CompressionUtil {

    public enum CompressionMethod {
        GZIP("GZIP"),
        BZIP2("BZIP2");

        private final String name;

        CompressionMethod(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
