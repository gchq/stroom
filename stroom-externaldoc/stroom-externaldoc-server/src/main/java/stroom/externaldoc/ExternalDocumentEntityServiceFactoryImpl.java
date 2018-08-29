package stroom.externaldoc;

import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerActionHandlerProvider;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportActionHandlerFactory;
import stroom.security.SecurityContext;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashSet;
import java.util.Set;

public class ExternalDocumentEntityServiceFactoryImpl implements ExplorerActionHandlerProvider, ImportExportActionHandlerFactory {
    private static final String ANNOTATIONS_INDEX = "AnnotationsIndex";
    private static final String ELASTIC_INDEX = "ElasticIndex";

    private final Provider<SecurityContext> securityContextProvider;
    private final UiConfig uiConfig;

    @Inject
    ExternalDocumentEntityServiceFactoryImpl(final Provider<SecurityContext> securityContextProvider, final UiConfig uiConfig) {
        this.securityContextProvider = securityContextProvider;
        this.uiConfig = uiConfig;
    }

    @Override
    public Set<ExplorerActionHandler> getExplorerActionHandlers() {
        final Set<ExplorerActionHandler> set = new HashSet<>();
        if (uiConfig != null && uiConfig.getUrlConfig() != null) {
            final UrlConfig urlConfig = uiConfig.getUrlConfig();
            if (urlConfig.getAnnotations() != null) {
                set.add(new ExternalDocumentEntityServiceImpl(ANNOTATIONS_INDEX, urlConfig.getAnnotations(), securityContextProvider.get()));
            }
            if (urlConfig.getElastic() != null) {
                set.add(new ExternalDocumentEntityServiceImpl(ELASTIC_INDEX, urlConfig.getElastic(), securityContextProvider.get()));
            }
        }
        return set;
    }

    @Override
    public Set<ImportExportActionHandler> getImportExportActionHandlers() {
        final Set<ImportExportActionHandler> set = new HashSet<>();
        if (uiConfig != null && uiConfig.getUrlConfig() != null) {
            final UrlConfig urlConfig = uiConfig.getUrlConfig();
            if (urlConfig.getAnnotations() != null) {
                set.add(new ExternalDocumentEntityServiceImpl(ANNOTATIONS_INDEX, urlConfig.getAnnotations(), securityContextProvider.get()));
            }
            if (urlConfig.getElastic() != null) {
                set.add(new ExternalDocumentEntityServiceImpl(ELASTIC_INDEX, urlConfig.getElastic(), securityContextProvider.get()));
            }
        }
        return set;
    }
}
