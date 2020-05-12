package stroom.kafka.impl;

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class KafkaConfigHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(KafkaConfigStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(KafkaConfigStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(KafkaConfigDoc.DOCUMENT_TYPE, KafkaConfigStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(KafkaConfigResourceImpl.class);
    }
}