package stroom.pipeline.refdata.util;

import stroom.util.shared.Clearable;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ByteBufferPool extends Clearable, HasSystemInfo {

    PooledByteBuffer getPooledByteBuffer(int minCapacity);

    PooledByteBufferPair getPooledBufferPair(int minKeyCapacity, int minValueCapacity);

    <T> T getWithBuffer(int minCapacity, Function<ByteBuffer, T> work);

    void doWithBuffer(int minCapacity, Consumer<ByteBuffer> work);

    int getCurrentPoolSize();

    @Override
    void clear();

    @Override
    SystemInfoResult getSystemInfo();
}
