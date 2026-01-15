/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.importexport.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ContentService;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportConfigResponse;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.DocRefs;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import com.google.common.base.Strings;
import event.logging.AdvancedQuery;
import event.logging.ComplexLoggedOutcome;
import event.logging.Criteria;
import event.logging.ExportEventAction;
import event.logging.Folder;
import event.logging.ImportEventAction;
import event.logging.MultiObject;
import event.logging.Or;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.Term;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@AutoLogged
public class ContentResourceImpl implements ContentResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentResourceImpl.class);

    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;
    private final Provider<ContentService> contentServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<SecurityContext> securityContextProvider;

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
    public ImportConfigResponse importContent(final ImportConfigRequest request) {
        if (request.getConfirmList() == null) {
            throw RestUtil.badRequest("Missing confirm list");
        }

        final ImportMode importMode = NullSafe.get(
                request,
                ImportConfigRequest::getImportSettings,
                ImportSettings::getImportMode);

        LOGGER.debug("Import mode: {}", importMode);

        final Supplier<ImportConfigResponse> responseSupplier = () ->
                contentServiceProvider.get()
                        .importContent(request);

        try {
            if (ImportMode.ACTION_CONFIRMATION.equals(importMode)
                && NullSafe.hasItems(request.getConfirmList())) {
                // Only want to log when the user has actually confirmed to import something
                return eventLoggingServiceProvider.get().loggedWorkBuilder()
                        .withTypeId("ImportConfig")
                        .withDescription("Importing Configuration")
                        .withDefaultEventAction(buildImportEventAction(request))
                        .withSimpleLoggedResult(responseSupplier)
                        .getResultAndLog();
            } else {
                return responseSupplier.get();
            }
        } catch (final Exception e) {
            LOGGER.error(LogUtil.message("Error importing content with key: {}, name: {}, error: {}",
                    NullSafe.get(request.getResourceKey(), ResourceKey::getKey),
                    NullSafe.get(request.getResourceKey(), ResourceKey::getName),
                    e.getMessage()), e);
            throw new RuntimeException(e);
        }
    }

    @AutoLogged(OperationType.UNLOGGED) // This is a tidy up operation so no need to log it
    @Override
    public void abortImport(final ResourceKey resourceKey) {
        contentServiceProvider.get().abortImport(resourceKey);
    }

    private ImportEventAction buildImportEventAction(final ImportConfigRequest importConfigRequest) {
        final List<ImportState> confirmList = importConfigRequest.getConfirmList();

        final String importFileName = NullSafe.get(importConfigRequest,
                ImportConfigRequest::getResourceKey,
                ResourceKey::getName);

        final List<OtherObject> objects = confirmList.stream()
                .map(importState ->
                        OtherObject.builder()
                                .withId(importState.getDocRef().getUuid())
                                .withType(importState.getDocRef().getType())
                                .withName(importState.getDocRef().getName())
                                .addData(EventLoggingUtil.createData(
                                        "ImportAction",
                                        NullSafe.toStringOrElse(
                                                importState.getState(),
                                                State::getDisplayValue,
                                                "Error")))
                                .build())
                .toList();

        return ImportEventAction.builder()
                .withSource(MultiObject.builder()
                        .addObject(objects)
                        .build())
                .addData(EventLoggingUtil.createData("FileName", importFileName))
                .build();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration exportContent(final DocRefs docRefs) {
        final MultiObject.Builder<Void> builder = MultiObject.builder();
        docRefs.getDocRefs().stream()
                .forEach(docRef -> {
                    final String path = securityContextProvider.get()
                                                .asProcessingUserResult(() -> explorerNodeServiceProvider.get().getPath(
                                                        docRef))
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

        return eventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId("ExportConfig")
                .withDescription("Exporting Configuration")
                .withDefaultEventAction(ExportEventAction.builder()
                        .withSource(builder.build())
                        .build())
                .withSimpleLoggedResult(() ->
                        contentServiceProvider.get().exportContent(docRefs))
                .getResultAndLog();
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
                                                .toList())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        return eventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "fetchDependencies"))
                .withDescription("List filtered dependencies")
                .withDefaultEventAction(SearchEventAction.builder()
                        .withQuery(buildRawQuery(criteria.getPartialName()))
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    final ResultPage<Dependency> result = contentServiceProvider.get()
                            .fetchDependencies(criteria);

                    final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
                            .withQuery(buildRawQuery(criteria.getPartialName()))
                            .withResultPage(StroomEventLoggingUtil.createResultPage(result))
                            .withTotalResults(BigInteger.valueOf(result.size()))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .getResultAndLog();
    }

    private Query buildRawQuery(final String userInput) {
        return Strings.isNullOrEmpty(userInput)
                ? new Query()
                : Query.builder()
                        .withRaw("Activity matches \""
                                 + Objects.requireNonNullElse(userInput, "")
                                 + "\"")
                        .build();
    }

}
