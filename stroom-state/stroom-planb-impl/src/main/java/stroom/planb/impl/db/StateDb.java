package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.db.State.Key;

import java.nio.file.Path;
import java.util.Optional;

public class StateDb extends AbstractLmdb<Key, StateValue> {

    public StateDb(final Path path,
                   final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true, false);
    }

    public StateDb(final Path path,
                   final ByteBufferFactory byteBufferFactory,
                   final boolean overwrite,
                   final boolean readOnly) {
        super(path, byteBufferFactory, new StateSerde(byteBufferFactory), overwrite, readOnly);
    }

    public Optional<State> getState(final StateRequest request) {
        final Key key = Key.builder().name(request.key()).build();
        final Optional<StateValue> optional = get(key);
        return optional.map(value -> new State(key, value));
    }
}
