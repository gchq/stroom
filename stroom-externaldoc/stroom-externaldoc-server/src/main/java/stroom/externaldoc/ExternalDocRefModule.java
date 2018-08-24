package stroom.externaldoc;

import com.google.inject.AbstractModule;
import stroom.explorer.ExplorerActionHandlerProvider;
import stroom.importexport.ImportExportActionHandlerFactory;

public class ExternalDocRefModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ExplorerActionHandlerProvider.class).to(ExternalDocumentEntityServiceFactoryImpl.class);
        bind(ImportExportActionHandlerFactory.class).to(ExternalDocumentEntityServiceFactoryImpl.class);
    }
}
