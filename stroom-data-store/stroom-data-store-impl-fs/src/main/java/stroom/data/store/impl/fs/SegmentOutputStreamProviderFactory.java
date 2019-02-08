package stroom.data.store.impl.fs;

interface SegmentOutputStreamProviderFactory {
    SegmentOutputStreamProvider getSegmentOutputStreamProvider(String streamTypeName);
}
