package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class S3ConfigHandlerModule extends AbstractModule {

    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(S3ConfigStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(S3ConfigStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(S3ConfigDoc.DOCUMENT_TYPE, S3ConfigStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(S3ConfigResourceImpl.class);
    }
}
