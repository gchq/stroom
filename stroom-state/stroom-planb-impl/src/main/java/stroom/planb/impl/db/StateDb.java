package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.db.State.Key;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;

import java.nio.file.Path;

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
        return new StateDb(path, byteBufferFactory, getSettings(doc), readOnly);
    }

    private static StateSettings getSettings(final PlanBDoc doc) {
        if (doc.getSettings() instanceof final StateSettings settings) {
            return settings;
        }
        return StateSettings.builder().build();
    }

    public State getState(final StateRequest request) {
        final Key key = Key.builder().name(request.key()).build();
        final StateValue value = get(key);
        if (value == null) {
            return null;
        }
        return new State(key, value);
    }
}
