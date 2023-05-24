package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.io.Output;

public interface OutputFactory {

    MyByteBufferOutput createOutput(int bufferSize, ErrorConsumer errorConsumer);

    Output createHashOutput(int bufferSize, ErrorConsumer errorConsumer);
}
