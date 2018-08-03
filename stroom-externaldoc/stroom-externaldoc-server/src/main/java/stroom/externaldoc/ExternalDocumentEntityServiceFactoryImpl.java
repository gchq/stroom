package stroom.externaldoc;

import stroom.explorer.ExplorerActionHandlerFactory;
import stroom.importexport.ImportExportActionHandlerFactory;
import stroom.logging.DocumentEventLog;
import stroom.properties.api.PropertyService;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import javax.inject.Provider;

public class ExternalDocumentEntityServiceFactoryImpl implements ExternalDocumentEntityServiceFactory, ExplorerActionHandlerFactory, ImportExportActionHandlerFactory {
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;
    private final Provider<PropertyService> propertyServiceProvider;

    @Inject
    ExternalDocumentEntityServiceFactoryImpl(final Provider<SecurityContext> securityContextProvider, final Provider<DocumentEventLog> documentEventLogProvider, final Provider<PropertyService> propertyServiceProvider) {
        this.securityContextProvider = securityContextProvider;
        this.documentEventLogProvider = documentEventLogProvider;
        this.propertyServiceProvider = propertyServiceProvider;
    }

    @Override
    public ExternalDocumentEntityService create(final String type) {
        return new ExternalDocumentEntityServiceImpl(type,
                securityContextProvider.get(),
                propertyServiceProvider.get());
    }
}
