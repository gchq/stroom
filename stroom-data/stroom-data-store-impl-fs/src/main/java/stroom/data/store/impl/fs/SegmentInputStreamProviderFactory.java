package stroom.data.store.impl.fs;

import java.util.Set;

public interface SegmentInputStreamProviderFactory {
    SegmentInputStreamProvider getSegmentInputStreamProvider(String streamTypeName);

    Set<String> getChildTypes();
}
