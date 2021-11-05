package stroom.lmdb;

import stroom.util.io.ByteSize;
import stroom.util.shared.HasPropertyPath;
import stroom.util.shared.NotInjectableConfig;

@NotInjectableConfig
public interface LmdbConfig extends HasPropertyPath {

    String LOCAL_DIR_PROP_NAME = "localDir";
    int DEFAULT_MAX_READERS = 126; // 126 is LMDB default
    ByteSize DEFAULT_MAX_STORE_SIZE = ByteSize.ofGibibytes(10);
    boolean DEFAULT_IS_READ_AHEAD_ENABLED = true;
    boolean DEFAULT_IS_READER_BLOCKED_BY_WRITER = true;

    String getLocalDir();

    int getMaxReaders();

    ByteSize getMaxStoreSize();

    boolean isReadAheadEnabled();

    boolean isReaderBlockedByWriter();
}
