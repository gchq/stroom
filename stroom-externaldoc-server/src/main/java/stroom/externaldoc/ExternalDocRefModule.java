package stroom.externaldoc;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Configuration;
import stroom.entity.EntityServiceBeanRegistry;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.logging.DocumentEventLog;
import stroom.node.shared.ClientProperties;
import stroom.properties.StroomPropertyService;
import stroom.security.SecurityContext;

import javax.inject.Inject;

public class ExternalDocRefModule extends AbstractModule {
    @Inject
    public ExternalDocRefModule(final EntityServiceBeanRegistry entityServiceBeanRegistry,
                                final SecurityContext securityContext,
                                final DocumentEventLog documentEventLog,
                                final StroomPropertyService propertyService) {

        propertyService.getCsvProperty(ClientProperties.EXTERNAL_DOC_REF_TYPES).stream()
                .map(type -> new ExternalDocumentEntityServiceImpl(type,
                        securityContext,
                        documentEventLog,
                        propertyService)
                ).forEach(service -> {
            entityServiceBeanRegistry.addExternal(service.getType(), service);
        });
    }

    @Override
    protected void configure() {
        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.externaldoc.ExternalDocumentEntityServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.externaldoc.ExternalDocumentEntityServiceImpl.class);
    }
}
