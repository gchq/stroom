package stroom.alert.impl;

import com.google.inject.AbstractModule;
import stroom.alert.api.AlertManager;

public class AlertModule  extends AbstractModule {
    @Override
    protected void configure() {
        bind(AlertManager.class).to(AlertManagerImpl.class);
    }
}

