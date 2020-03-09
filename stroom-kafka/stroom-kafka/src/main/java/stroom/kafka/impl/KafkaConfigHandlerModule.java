package stroom.kafka.impl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.kafka.pipeline.KafkaConfigStore;
import stroom.kafkaConfig.shared.KafkaConfigDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.RestResource;

public class KafkaConfigHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(KafkaConfigStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(KafkaConfigStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(KafkaConfigDoc.DOCUMENT_TYPE, KafkaConfigStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(KafkaConfigResourceImpl.class);
    }
}