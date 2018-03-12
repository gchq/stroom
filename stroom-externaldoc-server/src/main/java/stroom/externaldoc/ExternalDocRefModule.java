package stroom.externaldoc;

import com.google.inject.AbstractModule;
import stroom.explorer.ExplorerActionHandlerFactory;
import stroom.importexport.ImportExportActionHandlerFactory;

public class ExternalDocRefModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ExternalDocumentEntityServiceFactory.class).to(ExternalDocumentEntityServiceFactoryImpl.class);
        bind(ExplorerActionHandlerFactory.class).to(ExternalDocumentEntityServiceFactoryImpl.class);
        bind(ImportExportActionHandlerFactory.class).to(ExternalDocumentEntityServiceFactoryImpl.class);
    }
}
