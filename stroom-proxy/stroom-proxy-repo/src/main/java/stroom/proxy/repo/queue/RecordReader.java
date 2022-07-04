package stroom.proxy.repo.queue;

import java.util.List;

public interface RecordReader<T> {

    long read(long currentReadPos, long limit, List<T> readQueue);
}
