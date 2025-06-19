package stroom.processor.client.presenter;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class RestSaveQueue<K, V> implements HasHandlers {

    private final EventBus eventBus;
    private final Map<K, Boolean> setting = new HashMap<>();
    private final Map<K, V> nextValue = new HashMap<>();

    public RestSaveQueue(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void setValue(final K key, final V value) {
        nextValue.put(key, value);
        if (!setting.containsKey(key)) {
            setting.put(key, true);
            tryAndSetValue(key);
        }
    }

    private void tryAndSetValue(final K key) {
        final V value = nextValue.remove(key);
        if (value != null) {
            doAction(key, value, this::tryAndSetValue);

        } else {
            setting.remove(key);
        }
    }

    protected abstract void doAction(final K key, final V value, final Consumer<K> consumer);

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
