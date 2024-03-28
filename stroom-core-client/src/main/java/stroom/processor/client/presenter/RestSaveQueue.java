package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class RestSaveQueue<K, V, T> implements HasHandlers {

    private final EventBus eventBus;
    private final Supplier<Rest<T>> restSupplier;
    private final Map<K, Boolean> setting = new HashMap<>();
    private final Map<K, V> nextValue = new HashMap<>();

    /**
     * @param eventBus
     * @param restSupplier E.g. '{@code () -> restFactory.builder().forBoolean()}'
     */
    public RestSaveQueue(final EventBus eventBus,
                         final Supplier<Rest<T>> restSupplier) {
        this.eventBus = eventBus;
        this.restSupplier = restSupplier;
    }

    public void setValue(final K key, final V value) {
        nextValue.put(key, value);
        if (!setting.containsKey(key)) {
            setting.put(key, true);
            tryAndSetValue(key);
        }
    }

    private void tryAndSetValue(final K key) {
        V value = nextValue.remove(key);
        if (value != null) {
            Rest<T> rest = restSupplier.get();
            rest = rest
                    .onSuccess(res -> tryAndSetValue(key))
                    .onFailure(res -> {
                        AlertEvent.fireError(this, res.getMessage(), null);
                        tryAndSetValue(key);
                    });
            doAction(rest, key, value);

        } else {
            setting.remove(key);
        }
    }

    protected abstract void doAction(final Rest<T> rest,
                                     final K key,
                                     final V value);

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
