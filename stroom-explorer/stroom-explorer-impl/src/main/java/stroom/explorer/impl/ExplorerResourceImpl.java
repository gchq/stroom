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

import stroom.docref.DocContentHighlights;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.api.ThreadLocalLogState;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.api.ExplorerNodePermissionsService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.AddRemoveTagsRequest;
import stroom.explorer.shared.AdvancedDocumentFindRequest;
import stroom.explorer.shared.AdvancedDocumentFindWithPermissionsRequest;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentFindRequest;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeInfo;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceCopyRequest;
import stroom.explorer.shared.ExplorerServiceCreateRequest;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.explorer.shared.ExplorerServiceMoveRequest;
import stroom.explorer.shared.ExplorerServiceRenameRequest;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.FindInContentResult;
import stroom.explorer.shared.FindResult;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetPermission;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import com.google.common.base.Strings;
import event.logging.AddGroups;
import event.logging.AdvancedQuery;
import event.logging.AuthorisationActionType;
import event.logging.AuthoriseEventAction;
import event.logging.ComplexLoggedOutcome;
import event.logging.Group;
import event.logging.Permission;
import event.logging.PermissionAttribute;
import event.logging.Permissions;
import event.logging.Query;
import event.logging.RemoveGroups;
import event.logging.SearchEventAction;
import event.logging.Term;
import event.logging.TermCondition;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class ExplorerResourceImpl implements ExplorerResource {

    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    private final Provider<ExplorerNodePermissionsService> explorerNodePermissionsServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<DocumentPermissionService> documentPermissionServiceProvider;

    @Inject
    ExplorerResourceImpl(final Provider<ExplorerService> explorerServiceProvider,
                         final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                         final Provider<DocRefInfoService> docRefInfoServiceProvider,
                         final Provider<ExplorerNodePermissionsService> explorerNodePermissionsServiceProvider,
                         final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                         final Provider<DocumentPermissionService> documentPermissionServiceProvider) {
        this.explorerServiceProvider = explorerServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.explorerNodePermissionsServiceProvider = explorerNodePermissionsServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
    }

    @Override
    public ExplorerNode create(final ExplorerServiceCreateRequest request) {
        return explorerServiceProvider.get().create(
                request.getDocType(),
                request.getDocName(),
                request.getDestinationFolder(),
                request.getPermissionInheritance());
    }

    @Override
    public BulkActionResult delete(final ExplorerServiceDeleteRequest request) {
        final List<ExplorerNode> explorerNodes = request.getDocRefs()
                .stream()
                .map(docRef -> Objects.requireNonNull(
                        getFromDocRef(docRef),
                        () -> "No explorer node found for " + docRef))
                .toList();
        return explorerServiceProvider.get()
                .delete(explorerNodes);
    }

    @Override
    public BulkActionResult copy(final ExplorerServiceCopyRequest request) {
        return explorerServiceProvider.get().copy(
                request.getExplorerNodes(),
                request.getDestinationFolder(),
                request.isAllowRename(),
                request.getDocName(),
                request.getPermissionInheritance());
    }

    @Override
    public BulkActionResult move(final ExplorerServiceMoveRequest request) {
        return explorerServiceProvider.get().move(request.getExplorerNodes(),
                request.getDestinationFolder(),
                request.getPermissionInheritance());
    }

    @Override
    public ExplorerNode rename(final ExplorerServiceRenameRequest request) {
        return explorerServiceProvider.get().rename(request.getExplorerNode(), request.getDocName());
    }

    @Override
    public ExplorerNode updateNodeTags(final ExplorerNode explorerNode) {
        return explorerServiceProvider.get().updateTags(explorerNode);
    }

    @Override
    public void addTags(final AddRemoveTagsRequest request) {
        Objects.requireNonNull(request);
        explorerServiceProvider.get().addTags(request.getDocRefs(), request.getTags());
    }

    @Override
    public void removeTags(final AddRemoveTagsRequest request) {
        Objects.requireNonNull(request);
        explorerServiceProvider.get().removeTags(request.getDocRefs(), request.getTags());
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public ExplorerNodeInfo info(final DocRef docRef) {
        final DocRefInfo docRefInfo = docRefInfoServiceProvider.get()
                .info(docRef)
                .orElse(null);

        if (docRefInfo == null) {
            return null;
        } else {
            final ExplorerNode explorerNode = explorerServiceProvider.get().getFromDocRef(docRef)
                    .orElseThrow(() -> new RuntimeException("No explorerNode for " + docRef));
            return new ExplorerNodeInfo(explorerNode, docRefInfo);
        }
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public DocRef decorate(final DocRef docRef) {
        return NullSafe.get(docRef,
                docRef2 -> docRefInfoServiceProvider.get()
                        .decorate(docRef, true));
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public ExplorerNode getFromDocRef(final DocRef docRef) {
        return explorerServiceProvider.get().getFromDocRef(docRef).orElse(null);
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
    @AutoLogged(OperationType.UNLOGGED)
    public DocumentTypes fetchDocumentTypes() {
        final ExplorerService explorerService = explorerServiceProvider.get();
        final List<DocumentType> types = explorerService.getTypes();
        final List<DocumentType> visibleTypes = explorerService.getVisibleTypes();
        return new DocumentTypes(types, visibleTypes);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public Set<String> fetchExplorerNodeTags() {
        return explorerServiceProvider.get().getTags();
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public Set<String> fetchExplorerNodeTags(final List<DocRef> docRefs) {
        return explorerServiceProvider.get().getTags(docRefs, TagFetchMode.OR);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public Set<ExplorerNodePermissions> fetchExplorerPermissions(final List<ExplorerNode> explorerNodes) {
        return explorerNodePermissionsServiceProvider.get().fetchExplorerPermissions(explorerNodes);
    }

    @Override
    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    public FetchExplorerNodeResult fetchExplorerNodes(final FetchExplorerNodesRequest request) {
        final boolean loggingRequired = !Strings.isNullOrEmpty(request.getFilter().getNameFilter());
        ThreadLocalLogState.setLogged(!loggingRequired);

        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "fetchExplorerNodes"))
                .withDescription("Fetch explorer nodes using filter")
                .withDefaultEventAction(SearchEventAction.builder()
                        .withQuery(buildRawQuery(request.getFilter()))
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final FetchExplorerNodeResult result = explorerServiceProvider.get().getData(request);

                    final ExplorerTreeFilter requestFilter = request.getFilter();
                    final ExplorerTreeFilter qualifiedFilter = requestFilter.withNameFilter(
                            result.getQualifiedFilterInput());

                    // Ignore the previous searchEventAction as it didn't have anything useful on it
                    final SearchEventAction newSearchEventAction = SearchEventAction.builder()
                            .withQuery(buildRawQuery(qualifiedFilter))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .withLoggingRequiredWhen(loggingRequired)
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

    @Override
    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    public ResultPage<FindResult> find(final DocumentFindRequest request) {
        final boolean loggingRequired = !Strings.isNullOrEmpty(request.getFilter().getNameFilter());
        ThreadLocalLogState.setLogged(!loggingRequired);

        final Query query = buildRawQuery(request.getFilter());
        final SearchEventAction action = SearchEventAction.builder().withQuery(query).build();
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "find"))
                .withDescription("Find documents using filter")
                .withDefaultEventAction(SearchEventAction.builder().withQuery(query).build())
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final ResultPage<FindResult> result = explorerServiceProvider.get().find(request);

                    return ComplexLoggedOutcome.success(result, action);
                })
                .withLoggingRequiredWhen(loggingRequired)
                .getResultAndLog();
    }

    @Override
    public ResultPage<FindResult> advancedFind(final AdvancedDocumentFindRequest request) {
        final Query query = StroomEventLoggingUtil.convertExpression(request.getExpression());
        final SearchEventAction action = SearchEventAction.builder().withQuery(query).build();
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "advancedFind"))
                .withDescription("Advanced find documents using filter")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final ResultPage<FindResult> result = explorerServiceProvider.get().advancedFind(request);
                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();

//        final Query query = StroomEventLoggingUtil.convertExpression(request.getExpression());
//        final SearchEventAction action = SearchEventAction.builder().withQuery(query).build();
//        final Consumer<ComplexLoggedOutcome> consumer = complexLoggedOutcome -> {
//            stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
//                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "advancedFind"))
//                    .withDescription("Advanced find documents using filter")
//                    .withDefaultEventAction(action)
//                    .withComplexLoggedResult(searchEventAction -> {
//                        return complexLoggedOutcome;
//                    })
//                    .getResultAndLog();
//        };
//
//        try {
//            final ResultPage<FindResult> result = explorerServiceProvider.get().advancedFind(request);
//            consumer.accept(ComplexLoggedOutcome.success(result, action));
//            return result;
//        } catch (final RuntimeException e) {
//            consumer.accept(ComplexLoggedOutcome.failure(action, e.getMessage()));
//            throw e;
//        }
    }

    @Override
    public ResultPage<FindResultWithPermissions> advancedFindWithPermissions(
            final AdvancedDocumentFindWithPermissionsRequest request) {
        final Query query = StroomEventLoggingUtil.convertExpression(request.getExpression());
        final SearchEventAction action = SearchEventAction.builder().withQuery(query).build();
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "advancedFindWithPermissions"))
                .withDescription("Advanced find documents plus permissions using filter")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final ResultPage<FindResultWithPermissions> result =
                            explorerServiceProvider.get().advancedFindWithPermissions(request);

                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    @Override
    public ResultPage<FindInContentResult> findInContent(final FindInContentRequest request) {
        final Query query = Query.builder().withRaw(request.getFilter().getPattern()).build();
        final SearchEventAction action = SearchEventAction.builder().withQuery(query).build();
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "findInContent"))
                .withDescription("Find documents with requested content")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final ResultPage<FindInContentResult> result =
                            explorerServiceProvider.get().findInContent(request);

                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        final Query query = Query.builder().withAdvanced(AdvancedQuery.builder()
                        .addTerm(Term
                                .builder()
                                .withName("docType")
                                .withCondition(TermCondition.EQUALS)
                                .withValue(request.getDocRef().getType())
                                .build())
                        .addTerm(Term
                                .builder()
                                .withName("docUuid")
                                .withCondition(TermCondition.EQUALS)
                                .withValue(request.getDocRef().getUuid())
                                .build())
                        .addTerm(Term
                                .builder()
                                .withName("docName")
                                .withCondition(TermCondition.EQUALS)
                                .withValue(request.getDocRef().getName())
                                .build())
                        .addTerm(Term
                                .builder()
                                .withName("text")
                                .withCondition(TermCondition.CONTAINS)
                                .withValue(request.getFilter().getPattern())
                                .build())
                        .build())
                .build();

        final SearchEventAction action = SearchEventAction.builder().withQuery(query).build();
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "fetchHighlights"))
                .withDescription("Fetch highlights in content")
                .withDefaultEventAction(action)
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final DocContentHighlights result =
                            explorerServiceProvider.get().fetchHighlights(request);

                    return ComplexLoggedOutcome.success(result, action);
                })
                .getResultAndLog();
    }

    @Override
    public Boolean changeDocumentPermissions(final BulkDocumentPermissionChangeRequest request) {
        final AdvancedDocumentFindRequest advancedDocumentFindRequest = new AdvancedDocumentFindRequest.Builder()
                .requiredPermissions(Set.of(DocumentPermission.OWNER))
                .expression(request.getExpression())
                .pageRequest(PageRequest.unlimited())
                .build();
        final ResultPage<FindResult> resultPage =
                explorerServiceProvider.get().advancedFind(advancedDocumentFindRequest);
        for (final FindResult findResult : resultPage.getValues()) {
            final SingleDocumentPermissionChangeRequest singleDocumentPermissionChangeRequest =
                    new SingleDocumentPermissionChangeRequest(findResult.getDocRef(), request.getChange());
            changeDocumentPermissions(singleDocumentPermissionChangeRequest);
        }
        return true;
    }

    private PermissionAttribute getPermissionAttribute(final DocumentPermission permission) {
        if (permission == null) {
            return null;
        }
        return switch (permission) {
            case USE -> PermissionAttribute.READ;
            case VIEW -> PermissionAttribute.READ;
            case EDIT -> PermissionAttribute.WRITE;
            case DELETE -> PermissionAttribute.WRITE;
            case OWNER -> PermissionAttribute.OWNER;
        };
    }

    private Group createGroup(final UserRef userRef,
                              final DocumentPermission documentPermission) {
        final PermissionAttribute permissionAttribute = getPermissionAttribute(documentPermission);
        final Permission permission = Permission
                .builder()
                .withAllowAttributes(permissionAttribute)
                .withUser(StroomEventLoggingUtil.createUser(userRef))
                .build();
        return Group
                .builder()
                .withPermissions(Permissions.builder().addPermissions(permission).build())
                .build();
    }

    private Boolean changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        final AbstractDocumentPermissionsChange change = request.getChange();
        if (change instanceof final SetPermission setPermission) {
            final Group group = createGroup(setPermission.getUserRef(), setPermission.getPermission());
            final AuthoriseEventAction action;
            if (setPermission.getPermission() == null) {
                action = AuthoriseEventAction
                        .builder()
                        .withAction(AuthorisationActionType.MODIFY)
                        .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                        .withRemoveGroups(RemoveGroups.builder().addGroups(group).build())
                        .build();
            } else {
                action = AuthoriseEventAction
                        .builder()
                        .withAction(AuthorisationActionType.MODIFY)
                        .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                        .withAddGroups(AddGroups.builder().addGroups(group).build())
                        .build();
            }
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Set document permission")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService = documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else if (change instanceof final AddDocumentCreatePermission addDocumentCreatePermission) {
            final Permission permission = Permission
                    .builder()
                    .withAllowAttributes(PermissionAttribute.AUTHOR)
                    .withUser(StroomEventLoggingUtil.createUser(addDocumentCreatePermission.getUserRef()))
                    .withGroup(Group
                            .builder()
                            .withType(addDocumentCreatePermission.getDocumentType().getDisplayType())
                            .build())
                    .build();
            final Group group = Group
                    .builder()
                    .withPermissions(Permissions.builder().addPermissions(permission).build())
                    .build();
            final AuthoriseEventAction action = AuthoriseEventAction
                    .builder()
                    .withAction(AuthorisationActionType.MODIFY)
                    .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                    .withAddGroups(AddGroups.builder().addGroups(group).build())
                    .build();
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Add document create permission for folder")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService =
                                documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else if (change instanceof final RemoveDocumentCreatePermission removeDocumentCreatePermission) {
            final Permission permission = Permission
                    .builder()
                    .withAllowAttributes(PermissionAttribute.AUTHOR)
                    .withUser(StroomEventLoggingUtil.createUser(removeDocumentCreatePermission.getUserRef()))
                    .withGroup(Group
                            .builder()
                            .withType(removeDocumentCreatePermission.getDocumentType().getDisplayType())
                            .build())
                    .build();
            final Group group = Group
                    .builder()
                    .withPermissions(Permissions.builder().addPermissions(permission).build())
                    .build();
            final AuthoriseEventAction action = AuthoriseEventAction
                    .builder()
                    .withAction(AuthorisationActionType.MODIFY)
                    .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                    .withRemoveGroups(RemoveGroups.builder().addGroups(group).build())
                    .build();
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Remove document create permission for folder")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService =
                                documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else if (change instanceof final AddAllDocumentCreatePermissions addAllDocumentCreatePermissions) {
            final Permission permission = Permission
                    .builder()
                    .withAllowAttributes(PermissionAttribute.AUTHOR)
                    .withUser(StroomEventLoggingUtil.createUser(addAllDocumentCreatePermissions.getUserRef()))
                    .withGroup(Group.builder().withType(ExplorerConstants.ALL_CREATE_PERMISSIONS).build())
                    .build();
            final Group group = Group
                    .builder()
                    .withPermissions(Permissions.builder().addPermissions(permission).build())
                    .build();
            final AuthoriseEventAction action = AuthoriseEventAction
                    .builder()
                    .withAction(AuthorisationActionType.MODIFY)
                    .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                    .withAddGroups(AddGroups.builder().addGroups(group).build())
                    .build();
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Add all document create permissions for folder")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService =
                                documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else if (change instanceof final RemoveAllDocumentCreatePermissions removeAllDocumentCreatePermissions) {
            final Permission permission = Permission
                    .builder()
                    .withAllowAttributes(PermissionAttribute.AUTHOR)
                    .withUser(StroomEventLoggingUtil.createUser(removeAllDocumentCreatePermissions.getUserRef()))
                    .withGroup(Group.builder().withType(ExplorerConstants.ALL_CREATE_PERMISSIONS).build())
                    .build();
            final Group group = Group
                    .builder()
                    .withPermissions(Permissions.builder().addPermissions(permission).build())
                    .build();
            final AuthoriseEventAction action = AuthoriseEventAction
                    .builder()
                    .withAction(AuthorisationActionType.MODIFY)
                    .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                    .withRemoveGroups(RemoveGroups.builder().addGroups(group).build())
                    .build();
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Remove all document create permissions for folder")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService =
                                documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else if (change instanceof final AddAllPermissionsFrom addAllPermissionsFrom) {
            final Group group = Group
                    .builder()
                    .withType(addAllPermissionsFrom.getSourceDocRef().getType())
                    .withId(addAllPermissionsFrom.getSourceDocRef().getUuid())
                    .withName(addAllPermissionsFrom.getSourceDocRef().getName())
                    .build();
            final AuthoriseEventAction action = AuthoriseEventAction
                    .builder()
                    .withAction(AuthorisationActionType.MODIFY)
                    .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                    .withAddGroups(AddGroups.builder().addGroups(group).build())
                    .build();
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Copy all document permissions from other document")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService =
                                documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else if (change instanceof final SetAllPermissionsFrom setAllPermissionsFrom) {
            final Group group = Group
                    .builder()
                    .withType(setAllPermissionsFrom.getSourceDocRef().getType())
                    .withId(setAllPermissionsFrom.getSourceDocRef().getUuid())
                    .withName(setAllPermissionsFrom.getSourceDocRef().getName())
                    .build();
            final AuthoriseEventAction action = AuthoriseEventAction
                    .builder()
                    .withAction(AuthorisationActionType.MODIFY)
                    .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                    .withAddGroups(AddGroups.builder().addGroups(group).build())
                    .build();
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Set all document permissions from other document")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService =
                                documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else if (change instanceof final RemoveAllPermissions removeAllPermissions) {
            final AuthoriseEventAction action = AuthoriseEventAction
                    .builder()
                    .withAction(AuthorisationActionType.MODIFY)
                    .addObject(StroomEventLoggingUtil.createOtherObject(request.getDocRef()))
                    .build();
            return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "changeDocumentPermissions"))
                    .withDescription("Remove all permissions from document")
                    .withDefaultEventAction(action)
                    .withComplexLoggedResult(searchEventAction -> {
                        final DocumentPermissionService documentPermissionService =
                                documentPermissionServiceProvider.get();
                        final Boolean result = documentPermissionService.changeDocumentPermissions(request);
                        return ComplexLoggedOutcome.success(result, action);
                    })
                    .getResultAndLog();

        } else {
            throw new RuntimeException("Unexpected change " + change.getClass().getName());
        }
    }
}
