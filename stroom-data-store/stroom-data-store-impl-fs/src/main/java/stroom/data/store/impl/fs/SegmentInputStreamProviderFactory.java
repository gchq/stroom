package stroom.data.store.impl.fs;

public interface SegmentInputStreamProviderFactory {
    SegmentInputStreamProvider getSegmentInputStreamProvider(String streamTypeName);
}
