package stroom.importexport.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.importexport.api.ContentService;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.DocRefs;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import event.logging.AdvancedQuery;
import event.logging.Criteria;
import event.logging.ExportEventAction;
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
import javax.ws.rs.BadRequestException;

@AutoLogged
public class ContentResourceImpl implements ContentResource {
    final Provider<StroomEventLoggingService> eventLoggingServiceProvider;
    final Provider<ContentService> contentServiceProvider;

    @Inject
    ContentResourceImpl(final Provider<StroomEventLoggingService> eventLoggingServiceProvider,
                        final Provider<ContentService> contentServiceProvider) {
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
        this.contentServiceProvider = contentServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public ResourceKey importContent(final ImportConfigRequest request) {
        if (request.getConfirmList() == null || request.getConfirmList().isEmpty()) {
            throw new BadRequestException("Missing confirm list");
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
        return eventLoggingServiceProvider.get().loggedResult(
                "ExportConfig",
                "Exporting Configuration",
                ExportEventAction.builder()
                        .withSource(MultiObject.builder()
                                .addCriteria(buildCriteria(docRefs))
                                .build())
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
