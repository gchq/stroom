package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Input;
import stroom.dashboard.expression.v1.Output;
import stroom.dashboard.expression.v1.Val;

public interface DataStore {
    Items get();

    Items get(final RawKey key);

    long getSize();

    long getTotalSize();


    void add(Val[] values);

    boolean readPayload(Input input);

    void writePayload(Output output);

    void clear();

    CompletionState getCompletionState();
}
