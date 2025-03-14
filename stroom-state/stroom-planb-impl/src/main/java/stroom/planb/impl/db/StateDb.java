package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.db.State.Key;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;

import java.nio.file.Path;
import java.util.Optional;

public class StateDb extends AbstractDb<Key, StateValue> {

    StateDb(final Path path,
            final ByteBufferFactory byteBufferFactory) {
        this(
                path,
                byteBufferFactory,
                StateSettings.builder().build(),
                false);
    }

    public StateDb(final Path path,
            final ByteBufferFactory byteBufferFactory,
            final StateSettings settings,
            final boolean readOnly) {
        super(
                path,
                byteBufferFactory,
                new StateSerde(byteBufferFactory),
                settings.getMaxStoreSize(),
                settings.getOverwrite(),
                readOnly);
    }

    public static StateDb create(final Path path,
                                 final ByteBufferFactory byteBufferFactory,
                                 final PlanBDoc doc,
                                 final boolean readOnly) {
        if (doc.getSettings() instanceof final StateSettings stateSettings) {
            return new StateDb(path, byteBufferFactory, stateSettings, readOnly);
        } else {
            throw new RuntimeException("No state settings provided");
        }
    }

    public Optional<State> getState(final StateRequest request) {
        final Key key = Key.builder().name(request.key()).build();
        final Optional<StateValue> optional = get(key);
        return optional.map(value -> new State(key, value));
    }
}
