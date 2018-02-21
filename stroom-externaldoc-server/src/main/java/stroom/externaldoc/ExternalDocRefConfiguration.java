package stroom.externaldoc;

import org.springframework.context.annotation.Configuration;
import stroom.entity.EntityServiceBeanRegistry;
import stroom.explorer.ExplorerActionHandlers;
import stroom.importexport.ImportExportActionHandlers;
import stroom.logging.DocumentEventLog;
import stroom.properties.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Configuration
//@ComponentScan(basePackages = {"stroom.externaldoc.server"}, excludeFilters = {
//        // Exclude other configurations that might be found accidentally during
//        // a component scan as configurations should be specified explicitly.
//        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class ExternalDocRefConfiguration {
    @Inject
    public ExternalDocRefConfiguration(final EntityServiceBeanRegistry entityServiceBeanRegistry,
                                       final ExplorerActionHandlers explorerActionHandlers,
                                       final ImportExportActionHandlers importExportActionHandlers,
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
            explorerActionHandlers.add(30, service.getType(), service.getType(), service);
            importExportActionHandlers.add(service.getType(), service);
        });
    }
}
