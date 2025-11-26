package stroom.importexport.impl;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class ContentPackModule extends AbstractModule {

    @Override
    protected void configure() {
        //Startup with very low priority to ensure it starts after everything else
        //in particular
        LifecycleBinder.create(binder())
                .bindStartupTaskTo(ContentPackImportStartup.class, 6);
    }


    // --------------------------------------------------------------------------------


    private static class ContentPackImportStartup extends RunnableWrapper {

        @Inject
        ContentPackImportStartup(final ContentPackImport contentPackImport) {
            super(contentPackImport::startup);
        }
    }
}
