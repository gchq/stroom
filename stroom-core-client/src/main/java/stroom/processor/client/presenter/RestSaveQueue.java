package stroom.processor.client.presenter;

import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;

import java.util.HashMap;
import java.util.Map;

public abstract class RestSaveQueue {
    private final RestFactory restFactory;
    private final Map<Integer, Boolean> setting = new HashMap<>();
    private final Map<Integer, Integer> nextPriority = new HashMap<>();

    public RestSaveQueue(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void setPriority(final int id, final int priority) {
        nextPriority.put(id, priority);
        if (!setting.containsKey(id)) {
            setting.put(id, true);
            tryAndSetPriority();
        }
    }

    private void tryAndSetPriority() {
        Integer id = null;
        Integer priority = null;
        if (nextPriority.size() > 0) {
            id = nextPriority.keySet().iterator().next();
            priority = nextPriority.remove(id);
        }

        if (id != null && priority != null){
            Rest<?> rest = restFactory.create();
            rest = rest
                    .onSuccess(res -> tryAndSetPriority())
                    .onFailure(res -> tryAndSetPriority());
            doAction(rest, id, priority);

        } else {
            setting.remove(id);
        }
    }

    protected abstract void doAction(final Rest<?> rest, final Integer key, final Integer value);
}
