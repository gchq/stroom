package stroom.proxy.repo.queue;

public interface QueueMonitor {

    void setWritePos(long writePos);

    void setReadPos(long readPos);

    void setBufferPos(long bufferPos);
}
