package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.State.Key;

import java.nio.file.Path;
import java.util.Optional;

public class StateReader extends AbstractLmdbReader<Key, StateValue> {

    public StateReader(final Path path,
                       final ByteBufferFactory byteBufferFactory) {
        super(path, byteBufferFactory, new StateSerde(byteBufferFactory));
    }

    public synchronized Optional<State> getState(final StateRequest request) {
        final Key key = Key.builder().name(request.key()).build();
        final Optional<StateValue> optional = get(key);
        return optional.map(value -> new State(key, value));
    }
}
