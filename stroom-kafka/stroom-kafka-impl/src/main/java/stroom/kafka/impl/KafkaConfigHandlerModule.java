package stroom.kafka.impl;

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class KafkaConfigHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(KafkaConfigStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(KafkaConfigStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(KafkaConfigDoc.DOCUMENT_TYPE, KafkaConfigStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bindResource(KafkaConfigResourceImpl.class);
    }
}