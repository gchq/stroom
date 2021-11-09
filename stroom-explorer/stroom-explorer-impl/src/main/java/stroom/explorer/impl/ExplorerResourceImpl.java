/*
 * Copyright 2017 Crown Copyright
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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.api.ExplorerNodePermissionsService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceCopyRequest;
import stroom.explorer.shared.ExplorerServiceCreateRequest;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.explorer.shared.ExplorerServiceMoveRequest;
import stroom.explorer.shared.ExplorerServiceRenameRequest;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import event.logging.ComplexLoggedOutcome;
import event.logging.Query;
import event.logging.SearchEventAction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class ExplorerResourceImpl implements ExplorerResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerResourceImpl.class);

    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    private final Provider<ExplorerNodePermissionsService> explorerNodePermissionsServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    ExplorerResourceImpl(final Provider<ExplorerService> explorerServiceProvider,
                         final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                         final Provider<DocRefInfoService> docRefInfoServiceProvider,
                         final Provider<ExplorerNodePermissionsService> explorerNodePermissionsServiceProvider,
                         final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.explorerServiceProvider = explorerServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.explorerNodePermissionsServiceProvider = explorerNodePermissionsServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    public DocRef create(final ExplorerServiceCreateRequest request) {
        return explorerServiceProvider.get().create(request.getDocType(),
                request.getDocName(),
                request.getDestinationFolderRef(),
                request.getPermissionInheritance());
    }

    @Override
    public BulkActionResult delete(final ExplorerServiceDeleteRequest request) {
        return explorerServiceProvider.get().delete(request.getDocRefs());
    }

    @Override
    public BulkActionResult copy(final ExplorerServiceCopyRequest request) {
        return explorerServiceProvider.get().copy(request.getDocRefs(),
                request.getDestinationFolderRef(),
                request.getPermissionInheritance());
    }

    @Override
    public BulkActionResult move(final ExplorerServiceMoveRequest request) {
        return explorerServiceProvider.get().move(request.getDocRefs(),
                request.getDestinationFolderRef(),
                request.getPermissionInheritance());
    }

    @Override
    public DocRef rename(final ExplorerServiceRenameRequest request) {
        return explorerServiceProvider.get().rename(request.getDocRef(), request.getDocName());
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public DocRefInfo info(final DocRef docRef) {
        return docRefInfoServiceProvider.get().info(docRef).orElse(null);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public Set<DocRef> fetchDocRefs(final Set<DocRef> docRefs) {
        final Set<DocRef> result = new HashSet<>();
        if (docRefs != null) {
            for (final DocRef docRef : docRefs) {
                explorerNodeServiceProvider.get().getNode(docRef)
                        .map(ExplorerNode::getDocRef)
                        .ifPresent(result::add);
            }
        }

        return result;
    }

    @Override
    public DocumentTypes fetchDocumentTypes() {
        final List<DocumentType> nonSystemTypes = explorerServiceProvider.get().getNonSystemTypes();
        final List<DocumentType> visibleTypes = explorerServiceProvider.get().getVisibleTypes();
        return new DocumentTypes(nonSystemTypes, visibleTypes);
    }

    @Override
    public Set<ExplorerNodePermissions> fetchExplorerPermissions(final List<ExplorerNode> explorerNodes) {
        return explorerNodePermissionsServiceProvider.get().fetchExplorerPermissions(explorerNodes);
    }

    @Override
    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    public FetchExplorerNodeResult fetchExplorerNodes(final FindExplorerNodeCriteria request) {

        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder(
                StroomEventLoggingUtil.buildTypeId(this, "fetchExplorerNodes"),
                "Fetch explorer nodes using filter",
                SearchEventAction.builder()
                        .withQuery(buildRawQuery(request.getFilter()))
                        .build())
                .withLoggingRequired(!Strings.isNullOrEmpty(request.getFilter().getNameFilter()))
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final FetchExplorerNodeResult result = explorerServiceProvider.get().getData(request);

                    final ExplorerTreeFilter requestFilter = request.getFilter();
                    final ExplorerTreeFilter qualifiedFilter = new ExplorerTreeFilter(
                            requestFilter.getIncludedTypes(),
                            requestFilter.getTags(),
                            requestFilter.getRequiredPermissions(),
                            result.getQualifiedFilterInput(),
                            requestFilter.isNameFilterChange());

                    // Ignore the previous searchEventAction as it didn't have anything useful on it
                    final SearchEventAction newSearchEventAction = SearchEventAction.builder()
                            .withQuery(buildRawQuery(qualifiedFilter))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .getResultAndLog();
    }

    private Query buildRawQuery(final ExplorerTreeFilter explorerTreeFilter) {
        final String rawQuery = LogUtil.message("Node matches: \"{}\", included types: {}, tags: {}",
                explorerTreeFilter.getNameFilter(),
                Objects.requireNonNullElse(explorerTreeFilter.getIncludedTypes(), "[]"),
                Objects.requireNonNullElse(explorerTreeFilter.getTags(), "[]"));

        return Query.builder()
                .withRaw(rawQuery)
                .build();
    }

}
