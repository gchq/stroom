package stroom.lmdb.topic;

import stroom.bytebuffer.ByteBufferPool;
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.BasicLmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.serde.LongSerde;
import stroom.lmdb.serde.Serde;

import java.util.concurrent.atomic.AtomicLong;

public class LmdbQueue<T> {

//    private final BasicLmdbDb<Long, T> queueDb;
//    private final LmdbEnv lmdbEnvironment;
//    private final ByteBufferPool byteBufferPool;
//    private final LongSerde keySerde;
//    private final Serde<T> valueSerde;
//    private final AtomicLong lastPublishOffset = new AtomicLong();
//    private final AtomicLong lastConsumerOffset = new AtomicLong();
//    private final AtomicLong lastCommittedConsumerOffset = new AtomicLong();
//    private final DbWriter dbWriter;
//
//    private final BasicLmdbDb<Long, Void> consumerOffsetDb;

}
