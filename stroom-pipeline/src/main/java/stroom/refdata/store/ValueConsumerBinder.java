package stroom.refdata.store;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;
import stroom.refdata.RefDataValueByteBufferConsumer;
import stroom.refdata.store.onheapstore.RefDataValueConsumer;

public class ValueConsumerBinder {
    private final MapBinder<ValueConsumerId, RefDataValueConsumer.Factory> mapBinder;

    private ValueConsumerBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ValueConsumerId.class, RefDataValueConsumer.Factory.class);
    }

    public static ValueConsumerBinder create(final Binder binder) {
        return new ValueConsumerBinder(binder);
    }

    public <F extends RefDataValueConsumer.Factory> ValueConsumerBinder bind(final Integer id, final Class<F> handler) {
        mapBinder.addBinding(new ValueConsumerId(id)).to(handler);
        return this;
    }
}
