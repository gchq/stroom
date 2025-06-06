package stroom.planb.impl.serde.count;

import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.query.language.functions.Val;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface CountValuesSerde<T> {

    void newSingleValue(Instant instant,
                        long value,
                        Consumer<ByteBuffer> consumer);

    void addSingleValue(ByteBuffer byteBuffer,
                        Instant instant,
                        long value,
                        Consumer<ByteBuffer> consumer);

    void merge(ByteBuffer source,
               ByteBuffer destination,
               Consumer<ByteBuffer> consumer);

    Instant readInsertTime(ByteBuffer byteBuffer);

    Long getVal(Instant instant,
                ByteBuffer byteBuffer);

    void getValues(TemporalKey key,
                   ByteBuffer byteBuffer,
                   List<ValConverter<T>> valConverters,
                   Consumer<Val[]> consumer);
}
