package stroom.processor.client.presenter;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;

import java.util.HashMap;
import java.util.Map;

public abstract class RestSaveQueue<K, V> implements HasHandlers {
    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final Map<K, Boolean> setting = new HashMap<>();
    private final Map<K, V> nextValue = new HashMap<>();

    public RestSaveQueue(final EventBus eventBus, final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
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
            Rest<?> rest = restFactory.create();
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

    protected abstract void doAction(final Rest<?> rest, final K key, final V value);

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
