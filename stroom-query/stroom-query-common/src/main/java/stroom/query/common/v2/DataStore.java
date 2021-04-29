package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.concurrent.TimeUnit;

public interface DataStore {

    Items get();

    Items get(final Key key);

    long getSize();

    long getTotalSize();

    void add(Val[] values);

    boolean readPayload(Input input);

    void writePayload(Output output);

    boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException;

    void clear();

    CompletionState getCompletionState();
}
