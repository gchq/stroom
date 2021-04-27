package stroom.importexport.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ContentService;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.util.rest.RestUtil;
import stroom.util.shared.DocRefs;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import event.logging.AdvancedQuery;
import event.logging.Criteria;
import event.logging.ExportEventAction;
import event.logging.Folder;
import event.logging.ImportEventAction;
import event.logging.MultiObject;
import event.logging.Or;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.Term;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class ContentResourceImpl implements ContentResource {

    final Provider<StroomEventLoggingService> eventLoggingServiceProvider;
    final Provider<ContentService> contentServiceProvider;
    final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    final Provider<SecurityContext> securityContextProvider;

    @Inject
    ContentResourceImpl(final Provider<StroomEventLoggingService> eventLoggingServiceProvider,
                        final Provider<ContentService> contentServiceProvider,
                        final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                        final Provider<SecurityContext> securityContextProvider) {
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
        this.contentServiceProvider = contentServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public ResourceKey importContent(final ImportConfigRequest request) {
        if (request.getConfirmList() == null) {
            throw RestUtil.badRequest("Missing confirm list");
        }

        return eventLoggingServiceProvider.get().loggedResult(
                "ImportConfig",
                "Importing Configuration",
                buildImportEventAction(request),
                () -> contentServiceProvider.get().performImport(request.getResourceKey(), request.getConfirmList())
        );
    }

    private ImportEventAction buildImportEventAction(final ImportConfigRequest importConfigRequest) {
        final List<ImportState> confirmList = importConfigRequest.getConfirmList();

        return ImportEventAction.builder()
                .withSource(MultiObject.builder()
                        .addObject(confirmList.stream()
                                .map(importState -> OtherObject.builder()
                                        .withId(importState.getDocRef().getUuid())
                                        .withType(importState.getDocRef().getType())
                                        .withName(importState.getDocRef().getName())
                                        .addData(EventLoggingUtil.createData(
                                                "ImportAction",
                                                importState.getState() != null
                                                        ? importState.getState().getDisplayValue()
                                                        : "Error"))
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .build();
    }


    @AutoLogged(OperationType.IMPORT)
    @Override
    public List<ImportState> confirmImport(final ResourceKey resourceKey) {
        return contentServiceProvider.get().confirmImport(resourceKey);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration exportContent(final DocRefs docRefs) {
        final MultiObject.Builder<Void> builder = MultiObject.builder();
        docRefs.getDocRefs().stream()
                .forEach(docRef -> {
                    final String path = securityContextProvider.get()
                            .asProcessingUserResult(() -> explorerNodeServiceProvider.get().getPath(docRef))
                            .stream().map(ExplorerNode::getName).collect(Collectors.joining("/"))
                            + docRef.getName();

                    if ("Folder".equals(docRef.getType())) {
                        builder.addFolder(
                                Folder.builder()
                                        .withName(docRef.getName())
                                        .withPath(path)
                                        .withId(docRef.getUuid())
                                        .withDescription(docRef.toInfoString())
                                        .build()
                        );
                    } else {
                        builder.addObject(
                                OtherObject.builder()
                                        .withName(docRef.getName())
                                        .withType(docRef.getType())
                                        .withId(docRef.getUuid())
                                        .withDescription(path + " : " + docRef.toInfoString())
                                        .build()

                        );
                    }
                });

        return eventLoggingServiceProvider.get().loggedResult(
                "ExportConfig",
                "Exporting Configuration",
                ExportEventAction.builder()
                        .withSource(builder.build())
                        .build(),
                () -> contentServiceProvider.get().exportContent(docRefs));
    }

    private Criteria buildCriteria(final DocRefs docRefs) {
        return Criteria.builder()
                .withQuery(Query.builder()
                        .withAdvanced(AdvancedQuery.builder()
                                .addOr(Or.builder()
                                        .addTerm(docRefs.getDocRefs()
                                                .stream()
                                                .map(docRef -> Term.builder()
                                                        .withName(docRef.getName())
                                                        .withCondition(TermCondition.EQUALS)
                                                        .withValue(docRef.getUuid())
                                                        .build())
                                                .collect(Collectors.toList()))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return contentServiceProvider.get().fetchDependencies(criteria);
    }
}
