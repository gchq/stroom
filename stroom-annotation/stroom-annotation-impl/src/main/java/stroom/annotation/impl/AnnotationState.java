package stroom.annotation.impl;

import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;

import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(
        type = "Annotation",
        action = {EntityAction.UPDATE})
public class AnnotationState implements EntityEvent.Handler {

    private long lastChangeTime;

    public long getLastChangeTime() {
        return lastChangeTime;
    }

    @Override
    public void onChange(final EntityEvent event) {
        lastChangeTime = System.currentTimeMillis();
    }
}
