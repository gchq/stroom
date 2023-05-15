package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

public interface OutputFactory {

    Output createValueOutput(ErrorConsumer errorConsumer);

    UnsafeByteBufferOutput createByteBufferOutput(int bufferSize, ErrorConsumer errorConsumer);
}
