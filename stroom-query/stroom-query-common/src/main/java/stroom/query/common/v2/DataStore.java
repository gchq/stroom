package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public interface DataStore {

    Items get();

    Items get(final Key key);

    long getSize();

    long getTotalSize();


    void add(Val[] values);

    boolean readPayload(Input input);

    void writePayload(Output output);

    void clear();

    CompletionState getCompletionState();
}
