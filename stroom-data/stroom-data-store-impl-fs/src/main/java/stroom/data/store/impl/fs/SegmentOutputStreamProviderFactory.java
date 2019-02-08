package stroom.data.store.impl.fs;

public interface SegmentOutputStreamProviderFactory {
    SegmentOutputStreamProvider getSegmentOutputStreamProvider(String streamTypeName);
}
