package stroom.data.store.impl.fs;

interface SegmentInputStreamProviderFactory {
    SegmentInputStreamProvider getSegmentInputStreamProvider(String streamTypeName);
}
