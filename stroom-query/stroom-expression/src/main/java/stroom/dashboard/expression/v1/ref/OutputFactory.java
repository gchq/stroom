package stroom.dashboard.expression.v1.ref;

public interface OutputFactory {

    MyByteBufferOutput createOutput(int bufferSize, ErrorConsumer errorConsumer);
}
