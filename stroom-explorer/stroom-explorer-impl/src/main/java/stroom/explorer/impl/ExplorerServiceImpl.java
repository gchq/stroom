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
 *
 */

package stroom.explorer.impl;

import stroom.collection.api.CollectionService;
import stroom.docref.DocContentHighlights;
import stroom.docref.DocContentMatch;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.ExplorerFavService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerFields;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.Builder;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.explorer.shared.ExplorerResource.TagFetchMode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.FindInContentResult;
import stroom.explorer.shared.FindRequest;
import stroom.explorer.shared.FindResult;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.NodeFlag.NodeFlagGroups;
import stroom.explorer.shared.PermissionInheritance;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.suggestions.api.SuggestionsQueryHandler;
import stroom.svg.shared.SvgImage;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.logging.Metrics;
import stroom.util.logging.Metrics.LocalMetrics;
import stroom.util.shared.Clearable;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
class ExplorerServiceImpl
        implements ExplorerService, CollectionService, Clearable, SuggestionsQueryHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerServiceImpl.class);

    private static final Set<String> FOLDER_TYPES = Set.of(
            ExplorerConstants.SYSTEM,
            ExplorerConstants.FAVOURITES,
            ExplorerConstants.FOLDER);

    private final ExplorerNodeService explorerNodeService;
    private final ExplorerTreeModel explorerTreeModel;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;
    private final Provider<ExplorerDecorator> explorerDecoratorProvider;
    private final Provider<ExplorerFavService> explorerFavService;
    private final EntityEventBus entityEventBus;

    @Inject
    ExplorerServiceImpl(final ExplorerNodeService explorerNodeService,
                        final ExplorerTreeModel explorerTreeModel,
                        final ExplorerActionHandlers explorerActionHandlers,
                        final SecurityContext securityContext,
                        final ExplorerEventLog explorerEventLog,
                        final Provider<ExplorerDecorator> explorerDecoratorProvider,
                        final Provider<ExplorerFavService> explorerFavService,
                        final EntityEventBus entityEventBus) {
        this.explorerNodeService = explorerNodeService;
        this.explorerTreeModel = explorerTreeModel;
        this.explorerActionHandlers = explorerActionHandlers;
        this.securityContext = securityContext;
        this.explorerEventLog = explorerEventLog;
        this.explorerDecoratorProvider = explorerDecoratorProvider;
        this.explorerFavService = explorerFavService;
        this.entityEventBus = entityEventBus;

        explorerNodeService.ensureRootNodeExists();
    }

    @Override
    public FetchExplorerNodeResult getData(final FetchExplorerNodesRequest criteria) {
        final DurationTimer timer = DurationTimer.start();
        final LocalMetrics metrics = Metrics.createLocalMetrics(LOGGER.isDebugEnabled());
        try {
            if (LOGGER.isDebugEnabled()) {
                logOpenItems(
                        OpenItemsImpl.create(criteria.getOpenItems()),
                        OpenItemsImpl.create(criteria.getTemporaryOpenedItems()),
                        OpenItemsImpl.create(criteria.getEnsureVisible()));
            }

            // Get a copy of the master tree model, so we can add the favourites into it.
            final TreeModel masterTreeModelClone = explorerTreeModel.getModel().createMutableCopy();
            // See if we need to open any more folders to see nodes we want to ensure are visible.
            final Set<ExplorerNodeKey> forcedOpenItems = getForcedOpenItems(masterTreeModelClone, criteria);

            final Set<ExplorerNodeKey> allOpen = new HashSet<>();
            allOpen.addAll(NullSafe.set(criteria.getOpenItems()));
            allOpen.addAll(NullSafe.set(criteria.getTemporaryOpenedItems()));
            allOpen.addAll(NullSafe.set(forcedOpenItems));
            final OpenItems openItems = OpenItemsImpl.createWithForced(allOpen, forcedOpenItems);
            final FetchExplorerNodeResult result = getData(
                    criteria.getFilter(),
                    masterTreeModelClone,
                    openItems,
                    metrics,
                    criteria.getShowAlerts());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getData() metrics:\n{}", LogUtil.toPaddedMultiLine("  ", metrics.toString()));
                logResult(LOGGER::debug, criteria, result, timer.get());
            }
            return result;

        } catch (Exception e) {
            LOGGER.error("Error fetching nodes with criteria {}", criteria, e);
            throw e;
        }
    }

    private FetchExplorerNodeResult getData(final ExplorerTreeFilter filter,
                                            final TreeModel masterTreeModelClone,
                                            final OpenItems openItems,
                                            final LocalMetrics metrics,
                                            final boolean includeNodeInfo) {
        // Generate a hashset of all favourites for the user, so we can mark matching nodes with a star
        final Set<String> userFavouriteUuids = explorerFavService.get().getUserFavourites()
                .stream()
                .map(DocRef::getUuid)
                .collect(Collectors.toSet());
        buildFavouritesNode(masterTreeModelClone);

        final FilteredTreeModel filteredModel = new FilteredTreeModel(
                masterTreeModelClone.getId(),
                masterTreeModelClone.getCreationTime());

        // A transient holder for the filter, predicate
        final NodeInclusionChecker nodeInclusionChecker = new NodeInclusionChecker(securityContext, filter);

        // Recurse down the tree adding items that should be included
        final NodeStates nodeStates = addDescendants(
                null,
                null,
                masterTreeModelClone,
                filteredModel,
                filter,
                nodeInclusionChecker,
                false,
                openItems,
                userFavouriteUuids,
                0,
                includeNodeInfo,
                metrics);

        // Sort the tree model
        filteredModel.sort(this::getPriority);

        // If the name filter has changed then we want to temporarily expand all nodes.
        Set<ExplorerNodeKey> temporaryOpenItems;
        if (filter.isNameFilterChange()) {
            if (NullSafe.isBlankString(filter.getNameFilter()) || nodeStates.openNodes.isEmpty()) {
                temporaryOpenItems = Collections.emptySet();
            } else {
                temporaryOpenItems = new HashSet<>(nodeStates.openNodes);
                openItems.addAll(nodeStates.openNodes);
            }
        } else {
            temporaryOpenItems = null;
        }

        List<ExplorerNodeKey> openedItems = new ArrayList<>();
        List<ExplorerNode> rootNodes = addRoots(
                filteredModel,
                openItems,
                openedItems,
                metrics);

        rootNodes = decorateTree(
                rootNodes,
                filter,
                nodeInclusionChecker);

        rootNodes = ensureRootNodes(rootNodes, filter);

        if (LOGGER.isTraceEnabled()) {
            logOpenItems(
                    OpenItemsImpl.create(openedItems),
                    OpenItemsImpl.create(temporaryOpenItems),
                    null);
        }

        return new FetchExplorerNodeResult(
                rootNodes, openedItems, temporaryOpenItems, nodeInclusionChecker.getQualifiedNameFilterInput());
    }

    private void logResult(final Consumer<String> loggerFunc,
                           final FetchExplorerNodesRequest criteria,
                           final FetchExplorerNodeResult result,
                           final Duration duration) {
        final ExplorerTreeFilter filter = criteria.getFilter();
        final String template = """
                Returned FetchExplorerNodeResult in {}
                  user: {}
                  minDepth: {}
                  includedTypes: {}
                  includedTags: {}
                  requiredPermissions: {}
                  filterInput: '{}'
                  qualifiedFilterInput: '{}'
                  criteria open item count: {}
                  criteria temp open item count: {}
                  criteria ensure visible count: {}
                  result open item count: {}
                  result temp open item count: {}
                """;
        loggerFunc.accept(LogUtil.message(
                template,
                duration,
                securityContext.getUserIdentityForAudit(),
                criteria.getMinDepth(),
                filter.getIncludedTypes(),
                filter.getTags(),
                filter.getRequiredPermissions(),
                Objects.requireNonNullElse(filter.getNameFilter(), ""),
                Objects.requireNonNullElse(result.getQualifiedFilterInput(), ""),
                NullSafe.size(criteria.getOpenItems()),
                NullSafe.size(criteria.getTemporaryOpenedItems()),
                NullSafe.size(criteria.getEnsureVisible()),
                NullSafe.size(result.getOpenedItems()),
                NullSafe.size(result.getTemporaryOpenedItems())));
    }

    private static void logOpenItems(final OpenItems openItems,
                                     final OpenItems tempOpenItems,
                                     final OpenItems ensureVisible) {
        if (NullSafe.hasItems(openItems)) {
            LOGGER.trace(() -> LogUtil.message("openItems:\n{}", openItems));
        }
        if (NullSafe.hasItems(tempOpenItems)) {
            LOGGER.trace(() -> LogUtil.message("tempOpenItems:\n{}", openItems));
        }
        if (NullSafe.hasItems(ensureVisible)) {
            LOGGER.trace(() -> LogUtil.message("ensureVisible:\n{}", openItems));
        }
    }

    private static List<ExplorerNode> ensureRootNodes(final List<ExplorerNode> rootNodes,
                                                      final ExplorerTreeFilter filter) {
        // Now ensure we always have favourite system root nodes
        final List<ExplorerNode> rootNodesCopy = new ArrayList<>(rootNodes);
        final List<ExplorerNode> result = new ArrayList<>();
        ExplorerNode favouritesNode = ensureRootNode(rootNodesCopy, filter, ExplorerConstants.FAVOURITES_NODE);

        // We can't use the tree model to work out if the fav node has descendant issues or not
        // so we have to just check its direct children
        final boolean foundNodeInfo = NullSafe.stream(favouritesNode.getChildren())
                .anyMatch(ExplorerNode::hasDescendantNodeInfo);
        favouritesNode = favouritesNode.copy()
                .setNodeFlag(NodeFlag.DESCENDANT_NODE_INFO, foundNodeInfo)
                .build();

        result.add(favouritesNode);
        result.add(ensureRootNode(rootNodesCopy, filter, ExplorerConstants.SYSTEM_NODE));
        // Add any remaining nodes, e.g. searchables
        result.addAll(rootNodesCopy);
        return result;
    }

    private static ExplorerNode ensureRootNode(final List<ExplorerNode> nodes,
                                               final ExplorerTreeFilter filter,
                                               final ExplorerNode rootNodeConstant) {

        // We may have no nodes under Favourites/System so ensure they are always present.
        // If a quick filter is active then mark them as non-matches.
        return removeMatchingNode(nodes, rootNodeConstant)
                .orElseGet(() ->
                        rootNodeConstant.copy()
                                .addNodeFlag(NodeFlag.LEAF)
                                .setGroupedNodeFlag(
                                        NodeFlagGroups.FILTER_MATCH_PAIR,
                                        NullSafe.isBlankString(filter.getNameFilter()))
                                .build());
    }

    private static Optional<ExplorerNode> removeMatchingNode(final List<ExplorerNode> nodes,
                                                             final ExplorerNode targetNode) {
        List<ExplorerNode> list = NullSafe.list(nodes);

        for (int i = 0; i < list.size(); i++) {
            final ExplorerNode node = list.get(i);
            if (Objects.equals(node, targetNode)) {
                nodes.remove(i);
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private int getPriority(final ExplorerNode node) {
        final DocumentType documentType = explorerActionHandlers.getType(node.getType());
        if (documentType == null) {
            return Integer.MAX_VALUE;
        }

        return documentType.getGroup().getPriority();
    }

    private void buildFavouritesNode(final TreeModel treeModel) {
        final ExplorerNode favRootNode = ExplorerConstants.FAVOURITES_NODE;
        treeModel.addRoot(favRootNode);

        for (final DocRef favDocRef : explorerFavService.get().getUserFavourites()) {
            final ExplorerNode treeModelNode = treeModel.getNode(favDocRef.getUuid());
            final ExplorerNode childNode = treeModelNode.copy()
                    .rootNodeUuid(favRootNode)
                    .depth(1)
                    .addNodeFlag(NodeFlag.FAVOURITE)
                    .addNodeFlag(ExplorerFlags.getStandardFlagByDocType(favDocRef.getType()).orElse(null))
                    .build();
            treeModel.add(favRootNode, childNode);
        }
    }

    private List<ExplorerNode> decorateTree(final List<ExplorerNode> rootNodes,
                                            final ExplorerTreeFilter filter,
                                            final NodeInclusionChecker nodeInclusionChecker) {
        if (!rootNodes.isEmpty()) {
            final ExplorerNode rootNode = rootNodes.getLast();
            return rootNodes
                    .stream()
                    .map(node -> {
                        if (node == rootNode) {
                            return replaceRootNode(node, filter, nodeInclusionChecker);
                        }
                        return node;
                    })
                    .collect(Collectors.toList());
        } else {
            return Collections.singletonList(replaceRootNode(null, filter, nodeInclusionChecker));
        }
    }

    private ExplorerNode replaceRootNode(final ExplorerNode rootNode,
                                         final ExplorerTreeFilter filter,
                                         final NodeInclusionChecker nodeInclusionChecker) {
        final ExplorerNode.Builder rootNodeBuilder;
        if (rootNode != null) {
            rootNodeBuilder = rootNode.copy();
        } else {
            // If there is no root at this point then either the quick filter has filtered
            // everything out so System is not a match, or it is type filter with no matching types
            // in which case System would be considered a match as we only have non-matches when
            // using the QuickFilter
            rootNodeBuilder = ExplorerConstants.SYSTEM_NODE.copy()
                    .setGroupedNodeFlag(
                            NodeFlagGroups.FILTER_MATCH_PAIR,
                            NullSafe.isBlankString(filter.getNameFilter()));
        }

        if (filter != null
                && NullSafe.set(filter.getNodeFlags()).contains(NodeFlag.DATA_SOURCE)) {

            final ExplorerDecorator explorerDecorator = explorerDecoratorProvider.get();
            if (explorerDecorator != null) {
                // Unfortunately we need to create the nodes before we filter, as we
                // need to be able to filter on the node's tags
                final List<ExplorerNode> additionalNodes = explorerDecorator.list()
                        .stream()
                        .map(this::createSearchableNode)
                        .filter(nodeInclusionChecker.getFuzzyMatchPredicate())
                        .toList();

                if (NullSafe.hasItems(additionalNodes)) {
                    additionalNodes.forEach(rootNodeBuilder::addChild);
                }
            }
        }
        if (rootNode == null || !rootNode.hasNodeFlagGroup(NodeFlagGroups.EXPANDER_GROUP)) {
            if (!rootNodeBuilder.hasChildren()) {
                rootNodeBuilder.addNodeFlag(NodeFlag.LEAF);
            }
        }
        return rootNodeBuilder.build();
    }

    @Override
    public Optional<ExplorerNode> getFromDocRef(final DocRef docRef) {
        final Optional<ExplorerNode> optional = explorerNodeService.getNodeWithRoot(docRef);
        if (optional.isPresent()) {
            return optional;
        }

        final ExplorerDecorator explorerDecorator = explorerDecoratorProvider.get();
        if (explorerDecorator == null) {
            return Optional.empty();
        }

        return explorerDecorator.list()
                .stream()
                .filter(ref -> Objects.equals(docRef, ref))
                .findAny()
                .map(this::createSearchableNode);
    }

    private ExplorerNode createSearchableNode(final DocRef docRef) {
        return ExplorerNode
                .builder()
                .docRef(docRef)
                .addNodeFlag(NodeFlag.DATA_SOURCE)
                .addNodeFlag(NodeFlag.LEAF)
                .depth(1)
                .icon(SvgImage.DOCUMENT_SEARCHABLE)
                .build();
    }

    @Override
    public Set<DocRef> getChildren(final DocRef folder, final String type) {
        return getDescendants(folder, type, 0);
    }

    @Override
    public Set<DocRef> getDescendants(final DocRef folder, final String type) {
        return getDescendants(folder, type, Integer.MAX_VALUE);
    }

    private Set<DocRef> getDescendants(final DocRef folder, final String type, final int maxDepth) {
        final UnmodifiableTreeModel masterTreeModel = explorerTreeModel.getModel();
        if (masterTreeModel != null) {
            final Set<DocRef> refs = new HashSet<>();
            addChildren(folder, type, 0, maxDepth, masterTreeModel, refs);
            return refs;
        }

        return Collections.emptySet();
    }

    private void addChildren(final DocRef parent,
                             final String type,
                             final int depth,
                             final int maxDepth,
                             final UnmodifiableTreeModel treeModel,
                             final Set<DocRef> refs) {
        final List<DocRef> children = treeModel.getChildren(parent);
        if (children != null) {
            children.forEach(childDocRef -> {
                if (childDocRef.getType().equals(type)) {
                    if (securityContext.hasDocumentPermission(childDocRef.getUuid(), DocumentPermissionNames.USE)) {
                        refs.add(childDocRef);
                    }
                }

                if (depth < maxDepth) {
                    addChildren(childDocRef, type, depth + 1, maxDepth, treeModel, refs);
                }
            });
        }
    }

    private Set<ExplorerNodeKey> getForcedOpenItems(final TreeModel masterTreeModel,
                                                    final FetchExplorerNodesRequest criteria) {
        final Set<ExplorerNodeKey> forcedOpen = new HashSet<>();

        // Add parents of nodes that we have been requested to ensure are visible.
        if (criteria.getEnsureVisible() != null && !criteria.getEnsureVisible().isEmpty()) {
            for (final ExplorerNodeKey ensureVisible : criteria.getEnsureVisible()) {
                ExplorerNode parent = masterTreeModel.getParent(ensureVisible.getUuid());
                while (parent != null) {
                    forcedOpen.add(ExplorerNode.builder()
                            .docRef(parent.getDocRef())
                            .rootNodeUuid(ensureVisible.getRootNodeUuid())
                            .build()
                            .getUniqueKey());
                    parent = masterTreeModel.getParent(parent);
                }
            }
        }

        // Add nodes that should be forced open because they are deeper than the minimum expansion depth.
        if (criteria.getMinDepth() != null && criteria.getMinDepth() > 0) {
            forceMinDepthOpen(masterTreeModel, forcedOpen, null, null,
                    criteria.getMinDepth(), 1);
            forcedOpen.add(ExplorerConstants.FAVOURITES_NODE.getUniqueKey());
        }

        return forcedOpen;
    }

    private void forceMinDepthOpen(final TreeModel masterTreeModel,
                                   final Set<ExplorerNodeKey> forcedOpen,
                                   final ExplorerNode rootNode,
                                   final ExplorerNode parent,
                                   final int minDepth,
                                   final int depth) {
        final List<ExplorerNode> children = masterTreeModel.getChildren(parent);
        if (children != null) {
            for (final ExplorerNode child : children) {
                final ExplorerNode childWithRootNode = child.copy()
                        .rootNodeUuid(Objects.requireNonNullElse(rootNode, child))
                        .build();
                forcedOpen.add(childWithRootNode.getUniqueKey());
                if (minDepth > depth) {
                    forceMinDepthOpen(masterTreeModel,
                            forcedOpen,
                            rootNode == null
                                    ? child
                                    : rootNode,
                            child,
                            minDepth,
                            depth + 1);
                }
            }
        }
    }

    private NodeStates addDescendants(final ExplorerNode rootNode,
                                      final ExplorerNode parent,
                                      final TreeModel treeModelIn,
                                      final FilteredTreeModel treeModelOut,
                                      final ExplorerTreeFilter filter,
                                      final NodeInclusionChecker nodeInclusionChecker,
                                      final boolean ignoreNameFilter,
                                      final OpenItems allOpenItems,
                                      final Set<String> userFavourites,
                                      final int currentDepth,
                                      final boolean includeNodeInfo,
                                      final LocalMetrics metrics) {
        return metrics.measure("addDescendants", () -> {
            int added = 0;
            boolean foundChildNodeInfo = false;
            boolean foundFilterMatch = false;
            final Set<ExplorerNodeKey> openNodes = new HashSet<>();

            final List<ExplorerNode> children = treeModelIn.getChildren(parent);
            if (children != null) {
                // Add all children if the name filter has changed or the parent item is open.
                final boolean addAllChildren = (filter.isNameFilterChange() && filter.getNameFilter() != null)
                        || parent == null
                        || allOpenItems.isOpen(parent.getUniqueKey());

                // We need to add at least one item to the tree to be able to determine if the parent is a leaf node.
                final Iterator<ExplorerNode> iterator = children.iterator();
                while (iterator.hasNext() && (addAllChildren || added == 0)) {
                    final ExplorerNode child = iterator.next();
                    // We don't want to filter child items if the parent folder matches the name filter.
                    final boolean isFuzzyFilterMatch = nodeInclusionChecker.isFuzzyFilterMatch(child);
                    final boolean ignoreChildNameFilter = ignoreNameFilter || isFuzzyFilterMatch;

                    // Decorate the child with the root parent node, so the same child can be referenced in multiple
                    // parent roots (e.g. System or Favourites)
                    final ExplorerNode.Builder nodeBuilder = child.copy()
                            .rootNodeUuid(Objects.requireNonNullElse(rootNode, child))
                            .setNodeFlag(NodeFlag.FAVOURITE, userFavourites.contains(child.getUuid()))
                            .setGroupedNodeFlag(NodeFlagGroups.FILTER_MATCH_PAIR, isFuzzyFilterMatch);
                    if (includeNodeInfo) {
                        nodeBuilder.nodeInfoList(treeModelIn.getNodeInfo(child));
                    }

                    ExplorerNode decoratedChild = nodeBuilder.build();

                    // Recurse right down to find out if a descendant is being added and therefore if we need to
                    // include this as an ancestor.
                    final NodeStates result = addDescendants(
                            Objects.requireNonNullElse(rootNode, decoratedChild),
                            decoratedChild,
                            treeModelIn,
                            treeModelOut,
                            filter,
                            nodeInclusionChecker,
                            ignoreChildNameFilter,
                            allOpenItems,
                            userFavourites,
                            currentDepth + 1,
                            includeNodeInfo,
                            metrics);

                    openNodes.addAll(result.openNodes);
                    final Builder builder = decoratedChild.copy()
                            .setNodeFlag(NodeFlag.FOLDER, result.isFolder);

                    if (includeNodeInfo && result.hasIssues && result.isFolder) {
                        // Mark the node as having descendants with issues
                        builder.addNodeFlag(NodeFlag.DESCENDANT_NODE_INFO);
                    }
                    decoratedChild = builder.build();

                    if (result.isFolder && result.containsFilterMatch) {
                        openNodes.add(decoratedChild.getUniqueKey());
                    }
                    if (isFuzzyFilterMatch || result.containsFilterMatch) {
                        foundFilterMatch = true;
                    }

                    // See if child should be included in treeModelOut
                    if (isNodeIncluded(result.hasChildren, ignoreNameFilter, nodeInclusionChecker, child, metrics)) {
                        treeModelOut.add(parent, decoratedChild);
                        added++;
                        if (includeNodeInfo) {
                            if (result.hasIssues || hasDescendantNodeInfo(
                                    treeModelIn,
                                    nodeInclusionChecker,
                                    ignoreNameFilter,
                                    parent,
                                    Collections.singleton(child),
                                    metrics)) {
                                foundChildNodeInfo = true;
                            }
                        }
                    }
                }

                // The above loop may not look at all children so check the remaining
                // children to establish if any included nodes have issues
                if (includeNodeInfo && !foundChildNodeInfo && !addAllChildren) {
                    final Set<ExplorerNode> remainingIncludedChildNodes = new HashSet<>();
                    iterator.forEachRemaining(remainingIncludedChildNodes::add);

                    if (hasDescendantNodeInfo(
                            treeModelIn,
                            nodeInclusionChecker,
                            ignoreNameFilter,
                            parent,
                            remainingIncludedChildNodes,
                            metrics)) {
                        foundChildNodeInfo = true;
                    }
                }
            }
            return new NodeStates(
                    added > 0,
                    foundChildNodeInfo,
                    isFolder(parent),
                    foundFilterMatch,
                    openNodes);
        });
    }

    private boolean isFolder(final ExplorerNode explorerNode) {
        if (explorerNode != null) {
            final String type = NullSafe.get(explorerNode.getDocRef(), DocRef::getType);
            if (type != null) {
                return FOLDER_TYPES.contains(type);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * @return True if any one of childNodes (or their descendants) has node info and is included
     * in treeModelOut
     */
    private boolean hasDescendantNodeInfo(final TreeModel treeModelIn,
                                          final NodeInclusionChecker nodeInclusionChecker,
                                          final boolean ignoreNameFilter,
                                          final ExplorerNode parentNode,
                                          final Collection<ExplorerNode> childNodes,
                                          final LocalMetrics metrics) {

        return metrics.measure("hasDescendantNodeInfo", () -> {
            // See if any of childNodes (or their descendants) have node info in the master model
            boolean hasDescendantNodeInfo = false;
            final Set<ExplorerNode> childrenWithDescendantInfo = treeModelIn.getChildrenWithDescendantInfo(
                    parentNode, childNodes);

            if (NullSafe.hasItems(childrenWithDescendantInfo)) {
                // At least one descendant has node info, but there may be filter/perms limiting what
                // the user can see, or they are in un-opened branches, so we need to walk the full tree
                // to check based on what we can see
                for (final ExplorerNode childNode : childrenWithDescendantInfo) {
                    final boolean isNodeIncluded = metrics.measure("isNodeIncluded", () ->
                            nodeInclusionChecker.isNodeIncluded(ignoreNameFilter, childNode));
                    if (isNodeIncluded) {
                        // This child is included in the filtering and is known to have descendant node info,
                        // so we can mark as found and bail out of this level
                        hasDescendantNodeInfo = true;
                        break;
                    } else {
                        // Not included but its children might be so recurse those nodes known
                        // to have descendant node info
                        final Set<ExplorerNode> grandChildren = treeModelIn.getChildrenWithDescendantInfo(
                                childNode);
                        if (NullSafe.hasItems(grandChildren)) {
                            hasDescendantNodeInfo = hasDescendantNodeInfo(
                                    treeModelIn,
                                    nodeInclusionChecker,
                                    ignoreNameFilter,
                                    childNode,
                                    grandChildren,
                                    metrics);
                        }
                    }
                    if (hasDescendantNodeInfo) {
                        break;
                    }
                }
            }
            return hasDescendantNodeInfo;
        });
    }

    private boolean isNodeIncluded(final boolean hasChildren,
                                   final boolean ignoreNameFilter,
                                   final NodeInclusionChecker nodeInclusionChecker,
                                   final ExplorerNode child,
                                   final LocalMetrics metrics) {
        return metrics.measure("isNodeIncluded (hasChildren)", () ->
                hasChildren
                        || nodeInclusionChecker.isNodeIncluded(ignoreNameFilter, child));
    }

    private List<ExplorerNode> addRoots(final FilteredTreeModel filteredModel,
                                        final OpenItems openItems,
                                        final List<ExplorerNodeKey> openedItems,
                                        final LocalMetrics metrics) {
        return metrics.measure("addRoots", () -> {
            final List<ExplorerNode> rootNodes = new ArrayList<>();
            final List<ExplorerNode> children = filteredModel.getChildren(null);

            if (children != null) {
                final List<ExplorerNode> sortedChildren = children
                        .stream()
                        .sorted(Comparator.comparing(ExplorerNode::getName))
                        .toList();
                for (final ExplorerNode child : sortedChildren) {
                    final ExplorerNode copy =
                            addChildren(
                                    child,
                                    child,
                                    filteredModel,
                                    openItems,
                                    0,
                                    openedItems,
                                    metrics);
                    rootNodes.add(copy);
                }
            }
            return rootNodes;
        });
    }

    private ExplorerNode addChildren(final ExplorerNode rootNode,
                                     final ExplorerNode parent,
                                     final FilteredTreeModel filteredModel,
                                     final OpenItems openItems,
                                     final int currentDepth,
                                     final List<ExplorerNodeKey> openedItems,
                                     final LocalMetrics metrics) {
        return metrics.measure("addChildren", () -> {
            ExplorerNode.Builder builder = parent.copy();
            builder.depth(currentDepth);

            final ExplorerNodeKey parentNodeKey = parent.getUniqueKey();

            // Remember if this item was forced open. This allows us to filter down the items that were actually opened
            // in the filtered tree as a result of forcing.
            if (openItems.isForcedOpen(parentNodeKey)) {
                openedItems.add(parentNodeKey);
            }

            final List<ExplorerNode> children = filteredModel.getChildren(parent);
            if (!parent.hasNodeFlagGroup(NodeFlagGroups.EXPANDER_GROUP) && NullSafe.isEmptyCollection(children)) {
                builder.setGroupedNodeFlag(NodeFlagGroups.EXPANDER_GROUP, NodeFlag.LEAF);
            } else if (openItems.isOpen(parentNodeKey)) {
                final List<ExplorerNode> newChildren = new ArrayList<>();
                for (final ExplorerNode child : NullSafe.list(children)) {
                    final ExplorerNode copy = addChildren(
                            rootNode,
                            child,
                            filteredModel,
                            openItems,
                            currentDepth + 1,
                            openedItems,
                            metrics);
                    newChildren.add(copy);
                }

                builder.setGroupedNodeFlag(NodeFlagGroups.EXPANDER_GROUP, NodeFlag.OPEN);
                builder.children(newChildren);
                builder.rootNodeUuid(rootNode);
            } else {
                builder.setGroupedNodeFlag(NodeFlagGroups.EXPANDER_GROUP, NodeFlag.CLOSED);
            }

            return builder.build();
        });
    }

    @Override
    public ExplorerNode create(final String type,
                               final String name,
                               final ExplorerNode destinationFolder,
                               final PermissionInheritance permissionInheritance) {

        final DocRef folderRef = getDestinationFolderRef(destinationFolder);
        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);

        DocRef result;

        // Create the document.
        try {
            // Check that the user is allowed to create an item of this type in the destination folder.
            checkCreatePermission(getUUID(folderRef), type);
            // Create an item of the specified type in the destination folder.
            // This should fire a CREATE entity event
            result = handler.createDocument(name);
            explorerEventLog.create(type, name, result.getUuid(), folderRef, permissionInheritance, null);
        } catch (final RuntimeException e) {
            explorerEventLog.create(type, name, null, folderRef, permissionInheritance, e);
            throw e;
        }

        // Create the explorer node.
        explorerNodeService.createNode(result, folderRef, permissionInheritance);

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return ExplorerNode.builder()
                .docRef(result)
                .rootNodeUuid(destinationFolder != null
                        ? destinationFolder.getRootNodeUuid()
                        : null)
                .build();
    }

    /**
     * Returns the DocRef of a destination folder
     *
     * @param destinationFolder If null, looks up the default root node
     */
    private DocRef getDestinationFolderRef(final ExplorerNode destinationFolder) {
        if (destinationFolder != null) {
            return destinationFolder.getDocRef();
        }

        final ExplorerNode rootNode = explorerNodeService.getRoot();
        return rootNode != null
                ? rootNode.getDocRef()
                : null;
    }

    @Override
    public BulkActionResult copy(final List<ExplorerNode> explorerNodes,
                                 final ExplorerNode destinationFolder,
                                 final boolean allowRename,
                                 final String docName,
                                 final PermissionInheritance permissionInheritance) {
        final StringBuilder resultMessage = new StringBuilder();

        // Create a map to store source to destination document references.
        final Map<ExplorerNode, ExplorerNode> remappings = new HashMap<>();

        // Discover all affected folder children.
        final Map<ExplorerNode, List<ExplorerNode>> childMap = new HashMap<>();
        createChildMap(explorerNodes, childMap);

        // Perform a copy on the selected items.
        explorerNodes.forEach(sourceNode -> copy(
                sourceNode,
                destinationFolder,
                allowRename,
                docName,
                permissionInheritance,
                resultMessage,
                remappings));

        // Recursively copy any selected folders.
        explorerNodes.forEach(sourceNode -> recurseCopy(
                sourceNode,
                permissionInheritance,
                resultMessage,
                remappings,
                childMap));

        // Remap all dependencies for the copied items.
        remappings.values().forEach(newExplorerNode -> {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(newExplorerNode.getType());
            if (handler != null) {
                final HashMap<DocRef, DocRef> docRefRemappings = new HashMap<>();
                for (final var remapping : remappings.entrySet()) {
                    docRefRemappings.put(remapping.getKey().getDocRef(), remapping.getValue().getDocRef());
                }
                handler.remapDependencies(newExplorerNode.getDocRef(), docRefRemappings);
            }
        });

        // Make sure the tree model is rebuilt.
        remappings.values().forEach(newNode -> {
            // Although the copy above will have fired entity events, they were before the deps
            // get re-mapped. Thus, we need to let the exp tree know that deps may have changed
            EntityEvent.fire(entityEventBus, newNode.getDocRef(), EntityAction.CREATE_EXPLORER_NODE);
        });

        return new BulkActionResult(new ArrayList<>(remappings.values()), resultMessage.toString());
    }

    /**
     * Copy an item into a destination folder.
     *
     * @param sourceNode            The explorer node being copied
     * @param destinationFolder     The destination folder explorer node
     * @param permissionInheritance The mode of permission inheritance being used for the whole operation
     * @param resultMessage         Allow contribution to result message
     * @param remappings            A map to store source to destination document references
     */
    private void copy(final ExplorerNode sourceNode,
                      final ExplorerNode destinationFolder,
                      final boolean allowRename,
                      final String docName,
                      final PermissionInheritance permissionInheritance,
                      final StringBuilder resultMessage,
                      final Map<ExplorerNode, ExplorerNode> remappings) {

        final DocRef destinationFolderRef = getDestinationFolderRef(destinationFolder);

        try {
            // Ensure we haven't already copied this item as part of a folder copy.
            if (!remappings.containsKey(sourceNode)) {
                // Get a handler to perform the copy.
                final ExplorerActionHandler handler = explorerActionHandlers.getHandler(sourceNode.getType());

                // Check that the user is allowed to create an item of this type in the destination folder.
                checkCreatePermission(getUUID(destinationFolderRef), sourceNode.getType());

                // Find out names of other items in the destination folder.
                final Set<String> otherDestinationChildrenNames = explorerNodeService.getChildren(destinationFolderRef)
                        .stream()
                        .map(ExplorerNode::getDocRef)
                        .filter(docRef -> docRef.getType().equals(sourceNode.getType()))
                        .map(DocRef::getName)
                        .collect(Collectors.toSet());

                // Copy the item to the destination folder.
                String name = sourceNode.getDocRef().getName();
                if (allowRename && docName != null && !docName.trim().isEmpty()) {
                    name = docName;
                }
                final DocRef destinationDocRef = handler.copyDocument(
                        sourceNode.getDocRef(),
                        name,
                        !allowRename,
                        otherDestinationChildrenNames);
                explorerEventLog.copy(sourceNode.getDocRef(), destinationFolderRef, permissionInheritance,
                        null);

                // Create the explorer node
                if (destinationDocRef != null) {
                    // Copy the explorer node.
                    explorerNodeService.copyNode(sourceNode.getDocRef(),
                            destinationDocRef,
                            destinationFolderRef,
                            permissionInheritance);

                    // Record where the document got copied from -> to.
                    remappings.put(sourceNode, ExplorerNode.builder()
                            .docRef(destinationDocRef)
                            .rootNodeUuid(destinationFolder != null
                                    ? destinationFolder.getRootNodeUuid()
                                    : null)
                            .build());
                }
            }

        } catch (final RuntimeException e) {
            explorerEventLog.copy(sourceNode.getDocRef(), destinationFolderRef, permissionInheritance, e);
            resultMessage.append("Unable to copy '");
            resultMessage.append(sourceNode.getName());
            resultMessage.append("' ");
            resultMessage.append(e.getMessage());
            resultMessage.append("\n");
        }
    }

    /**
     * Copy the contents of a folder recursively
     *
     * @param sourceFolder          The explorer node being copied
     * @param permissionInheritance The mode of permission inheritance being used for the whole operation
     * @param resultMessage         Allow contribution to result message
     * @param remappings            A map to store source to destination explorer nodes
     * @param childMap              A map of folders and their child items.
     */
    private void recurseCopy(final ExplorerNode sourceFolder,
                             final PermissionInheritance permissionInheritance,
                             final StringBuilder resultMessage,
                             final Map<ExplorerNode, ExplorerNode> remappings,
                             final Map<ExplorerNode, List<ExplorerNode>> childMap) {
        final ExplorerNode destinationFolder = remappings.get(sourceFolder);
        if (destinationFolder != null) {
            final List<ExplorerNode> children = childMap.get(sourceFolder);
            if (children != null && !children.isEmpty()) {
                children.forEach(child -> {
                    copy(child, destinationFolder, false, null, permissionInheritance, resultMessage, remappings);
                    recurseCopy(child, permissionInheritance, resultMessage, remappings, childMap);
                });
            }
        }
    }

    /**
     * Create a map of folders and their child items.
     */
    private void createChildMap(final List<ExplorerNode> explorerNodes,
                                final Map<ExplorerNode, List<ExplorerNode>> childMap) {
        explorerNodes.forEach(explorerNode -> {
            final List<ExplorerNode> children = explorerNodeService.getChildren(explorerNode.getDocRef());
            if (children != null && !children.isEmpty()) {
                childMap.put(explorerNode, children);
                createChildMap(children, childMap);
            }
        });
    }

    @Override
    public BulkActionResult move(final List<ExplorerNode> explorerNodes,
                                 final ExplorerNode destinationFolder,
                                 final PermissionInheritance permissionInheritance) {
        final DocRef folderRef = getDestinationFolderRef(destinationFolder);
        final List<ExplorerNode> resultNodes = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        // Test whether any of the source items match the destination. If so, abort the move as it's not possible
        // to move one object into itself
        if (explorerNodes.stream().anyMatch(node -> node.getDocRef().equals(destinationFolder.getDocRef()))) {
            throw new IllegalArgumentException("Source and destination locations cannot be the same.");
        }

        for (final ExplorerNode explorerNode : explorerNodes) {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(explorerNode.getType());

            DocRef result = null;

            try {
                // Check that the user is allowed to create an item of this type in the destination folder.
                checkCreatePermission(getUUID(folderRef), explorerNode.getType());
                // Move the item.
                result = handler.moveDocument(explorerNode.getUuid());
                explorerEventLog.move(explorerNode.getDocRef(), folderRef, permissionInheritance, null);
                resultNodes.add(ExplorerNode.builder()
                        .docRef(result)
                        .rootNodeUuid(explorerNode.getRootNodeUuid())
                        .build());

            } catch (final RuntimeException e) {
                explorerEventLog.move(explorerNode.getDocRef(), folderRef, permissionInheritance, e);
                resultMessage.append("Unable to move '");
                resultMessage.append(explorerNode.getName());
                resultMessage.append("' ");
                resultMessage.append(e.getMessage());
                resultMessage.append("\n");
            }

            // Create the explorer node
            if (result != null) {
                explorerNodeService.moveNode(result, folderRef, permissionInheritance);
            }
            // Let the tree know it has changed
            EntityEvent.fire(entityEventBus, explorerNode.getDocRef(), result, EntityAction.UPDATE_EXPLORER_NODE);
        }

        return new BulkActionResult(resultNodes, resultMessage.toString());
    }

    @Override
    public ExplorerNode rename(final ExplorerNode explorerNode, final String docName) {
        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(explorerNode.getType());
        final ExplorerNode result = rename(handler, explorerNode, docName);

        // Make sure the tree model is rebuilt.
        EntityEvent.fire(entityEventBus, result.getDocRef(), EntityAction.UPDATE_EXPLORER_NODE);

        return result;
    }

    @Override
    public ExplorerNode updateTags(final ExplorerNode explorerNode) {
        Objects.requireNonNull(explorerNode);
        final DocRef docRef = explorerNode.getDocRef();
        return updateTags(docRef, explorerNode.getTags());
    }

    public ExplorerNode updateTags(final DocRef docRef, final Set<String> tags) {
        Objects.requireNonNull(docRef);
        ExplorerNode beforeNode = null;
        ExplorerNode afterNode = null;
        try {
            beforeNode = explorerNodeService.getNode(docRef)
                    .orElse(null);

            explorerNodeService.updateTags(docRef, tags);

            afterNode = explorerNodeService.getNode(docRef)
                    .orElseThrow(() -> new RuntimeException(LogUtil.message(
                            "Can't find node {} after updating it", docRef)));

            explorerEventLog.update(beforeNode, afterNode, null);

            // Make sure the tree model is rebuilt.
            EntityEvent.fire(entityEventBus, docRef, EntityAction.UPDATE_EXPLORER_NODE);
            return afterNode;
        } catch (final Exception e) {
            explorerEventLog.update(beforeNode, afterNode, e);
            throw e;
        }
    }

    @Override
    public void addTags(final List<DocRef> docRefs, final Set<String> tags) {
        addRemoveTags(docRefs, tags, TagOperation.ADD);
    }

    @Override
    public void removeTags(final List<DocRef> docRefs, final Set<String> tags) {
        addRemoveTags(docRefs, tags, TagOperation.REMOVE);
    }

    private void addRemoveTags(final List<DocRef> docRefs,
                               final Set<String> tags,
                               final TagOperation tagOperation) {

        if (NullSafe.hasItems(tags)) {
            for (final DocRef docRef : NullSafe.list(docRefs)) {

                explorerNodeService.getNode(docRef).ifPresent(node -> {
                    final Set<String> nodeTags = new HashSet<>(NullSafe.set(node.getTags()));
                    if (TagOperation.ADD.equals(tagOperation)) {
                        nodeTags.addAll(tags);
                    } else {
                        nodeTags.removeAll(tags);
                    }

                    updateTags(docRef, nodeTags);
                });
            }
        }
    }

    private ExplorerNode rename(final ExplorerActionHandler handler,
                                final ExplorerNode explorerNode,
                                final String docName) {
        DocRef result;

        try {
            result = handler.renameDocument(explorerNode.getUuid(), docName);
            explorerEventLog.rename(explorerNode.getDocRef(), docName, null);
        } catch (final RuntimeException e) {
            explorerEventLog.rename(explorerNode.getDocRef(), docName, e);
            throw e;
        }

        // Rename the explorer node.
        explorerNodeService.renameNode(result);
        EntityEvent.fire(entityEventBus, result, EntityAction.UPDATE_EXPLORER_NODE);

        return ExplorerNode.builder()
                .docRef(result)
                .rootNodeUuid(explorerNode.getRootNodeUuid())
                .build();
    }

    @Override
    public BulkActionResult delete(final List<ExplorerNode> explorerNodes) {
        final List<ExplorerNode> resultNodes = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        final HashSet<ExplorerNode> deleted = new HashSet<>();
        explorerNodes.forEach(explorerNode -> {
            // Check this document hasn't already been deleted.
            if (!deleted.contains(explorerNode)) {
                recursiveDelete(explorerNodes, deleted, resultNodes, resultMessage);
            }
        });

        // The action handlers may fire DELETE events, but in case they don't
        resultNodes.stream()
                .filter(Objects::nonNull)
                .map(ExplorerNode::getDocRef)
                .forEach(docRef ->
                        EntityEvent.fire(entityEventBus, docRef, EntityAction.DELETE_EXPLORER_NODE));

        return new BulkActionResult(resultNodes, resultMessage.toString());
    }

    private void recursiveDelete(final List<ExplorerNode> explorerNodes,
                                 final HashSet<ExplorerNode> deleted,
                                 final List<ExplorerNode> resultDocRefs,
                                 final StringBuilder resultMessage) {
        explorerNodes.forEach(explorerNode -> {
            // Check this document hasn't already been deleted.
            if (!deleted.contains(explorerNode)) {
                // Get any children that might need to be deleted.
                List<ExplorerNode> children = explorerNodeService.getChildren(explorerNode.getDocRef());
                if (children != null && !children.isEmpty()) {
                    // Recursive delete.
                    recursiveDelete(children, deleted, resultDocRefs, resultMessage);
                }

                // Check to see if we still have children.
                children = explorerNodeService.getChildren(explorerNode.getDocRef());
                if (children != null && !children.isEmpty()) {
                    final String message = "Unable to delete '" + explorerNode.getName() +
                            "' because the folder is not empty";
                    resultMessage.append(message);
                    resultMessage.append("\n");
                    explorerEventLog.delete(explorerNode.getDocRef(), new RuntimeException(message));

                } else {
                    final ExplorerActionHandler handler = explorerActionHandlers.getHandler(explorerNode.getType());
                    try {
                        handler.deleteDocument(explorerNode.getUuid());
                        explorerEventLog.delete(explorerNode.getDocRef(), null);
                        deleted.add(explorerNode);
                        resultDocRefs.add(explorerNode);

                        // Delete the explorer node.
                        explorerNodeService.deleteNode(explorerNode.getDocRef());

                    } catch (final Exception e) {
                        explorerEventLog.delete(explorerNode.getDocRef(), e);
                        resultMessage.append("Unable to delete '");
                        resultMessage.append(explorerNode.getName());
                        resultMessage.append("' ");
                        resultMessage.append(e.getMessage());
                        resultMessage.append("\n");
                    }
                }
            }
        });
    }

    @Override
    public void rebuildTree() {
        explorerTreeModel.rebuild();
    }

    @Override
    public void clear() {
        explorerTreeModel.clear();
    }

    @Override
    public List<DocumentType> getTypes() {
        return explorerActionHandlers.getTypes();
    }

    @Override
    public Set<String> getTags() {
        final Set<String> modelTags = NullSafe.set(explorerTreeModel.getModel().getAllTags());
        final int count = StandardExplorerTags.values().length + modelTags.size();
        final Set<String> tags = new HashSet<>(count);

        // Add in our standard tags
        for (final StandardExplorerTags tag : StandardExplorerTags.values()) {
            tags.add(tag.getTagName());
        }

        // Add in all known tags from the exp tree model, i.e. user added ones
        tags.addAll(modelTags);
        return Collections.unmodifiableSet(tags);
    }

    @Override
    public Set<String> getTags(final Collection<DocRef> docRefs, final TagFetchMode fetchMode) {

        if (NullSafe.hasItems(docRefs)) {
            final UnmodifiableTreeModel treeModel = explorerTreeModel.getModel();
            if (TagFetchMode.OR.equals(fetchMode) || docRefs.size() == 1) {
                return NullSafe.stream(docRefs)
                        .filter(Objects::nonNull)
                        .map(docRef ->
                                treeModel.getNode(docRef.getUuid()))
                        .filter(Objects::nonNull)
                        .flatMap(node ->
                                NullSafe.stream(node.getTags()))
                        .collect(Collectors.toUnmodifiableSet());
            } else {
                // Find the tags common to ALL nodes
                Set<String> commonTags = null;
                for (final DocRef docRef : docRefs) {
                    if (docRef != null) {
                        final ExplorerNode node = treeModel.getNode(docRef.getUuid());
                        final Set<String> nodeTags = new HashSet<>(NullSafe.getOrElseGet(
                                node,
                                ExplorerNode::getTags,
                                Collections::emptySet));
                        if (nodeTags.isEmpty()) {
                            // A node has no tags so no common tags
                            commonTags = nodeTags;
                            break;
                        }
                        if (commonTags != null) {
                            nodeTags.retainAll(commonTags);
                        }
                        commonTags = nodeTags;
                    }
                }
                return Collections.unmodifiableSet(NullSafe.set(commonTags));
            }
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public List<DocumentType> getVisibleTypes() {
        // Get the master tree model.
        final UnmodifiableTreeModel masterTreeModel = explorerTreeModel.getModel();

        // Filter the model by user permissions.
        final Set<String> requiredPermissions = new HashSet<>();
        requiredPermissions.add(DocumentPermissionNames.READ);

        final Set<String> visibleTypes = new HashSet<>();
        addTypes(null, masterTreeModel, visibleTypes, requiredPermissions);

        return getDocumentTypes(visibleTypes);
    }

    private boolean addTypes(final ExplorerNode parent,
                             final UnmodifiableTreeModel treeModel,
                             final Set<String> types,
                             final Set<String> requiredPermissions) {
        boolean added = false;

        final List<ExplorerNode> children = treeModel.getChildren(parent);
        if (children != null) {
            for (final ExplorerNode child : children) {
                // Recurse right down to find out if a descendant is being added and therefore if we need to
                // include this type as it is an ancestor.
                // Even if you have no permission on a folder, you can see descendants of it if you have permission
                // on them, hence we have to recurse all the way.
                final boolean hasChildren = addTypes(child, treeModel, types, requiredPermissions);
                final String type = child.getType();
                if (hasChildren) {
                    // We added one or more descendants of child, so add child's type
                    types.add(type);
                    added = true;
                } else if (!types.contains(type)
                        && NodeInclusionChecker.hasPermission(securityContext, child, requiredPermissions)) {
                    types.add(type);
                    added = true;
                }
            }
        }

        return added;
    }

    private List<DocumentType> getDocumentTypes(final Collection<String> visibleTypes) {
        return getTypes().stream()
                .filter(type -> visibleTypes.contains(type.getType()))
                .collect(Collectors.toList());
    }

    private String getUUID(final DocRef docRef) {
        return Optional.ofNullable(docRef)
                .map(DocRef::getUuid)
                .orElse(null);
    }

    private void checkCreatePermission(final String folderUUID, final String type) {
        // Only allow administrators to create documents with no folder.
        if (folderUUID == null) {
            if (!securityContext.isAdmin()) {
                throw new PermissionException(securityContext.getUserIdentityForAudit(),
                        "Only administrators can create root level entries");
            }
        } else {
            if (!securityContext.hasDocumentPermission(folderUUID,
                    DocumentPermissionNames.getDocumentCreatePermission(type))) {
                final String folderName = Optional.ofNullable(explorerTreeModel.getModel().getNode(folderUUID))
                        .map(ExplorerNode::getName)
                        .filter(name -> !name.isEmpty())
                        .map(name -> "'" + name + "' (" + folderUUID + ")")
                        .orElse(folderUUID);
                throw new PermissionException(securityContext.getUserIdentityForAudit(),
                        "You do not have permission to create " + type + " in folder " + folderName);
            }
        }
    }

    @Override
    public ResultPage<FindResult> find(final FindRequest request) {
        final LocalMetrics metrics = Metrics.createLocalMetrics(LOGGER.isDebugEnabled());
        try {
            if (request.getFilter() == null) {
                return ResultPage.empty();
            }
            final boolean recentItemsMode = request.getFilter().getRecentItems() != null;
            if (recentItemsMode) {
                if (request.getFilter().getRecentItems().isEmpty()) {
                    return ResultPage.empty();
                }
            } else if (NullSafe.isBlankString(request.getFilter().getNameFilter())) {
                return ResultPage.empty();
            }

            // Get a copy of the master tree model, so we can add the favourites into it.
            final TreeModel masterTreeModelClone = explorerTreeModel.getModel().createMutableCopy();

            final FetchExplorerNodeResult result = getData(
                    request.getFilter(),
                    masterTreeModelClone,
                    OpenItemsImpl.all(),
                    metrics,
                    false);
            final List<FindResult> results = new ArrayList<>();
            addResults("", result.getRootNodes(), results);

            // If this is recent items mode then filter by recent items.
            if (recentItemsMode) {
                final Map<DocRef, FindResult> resultMap = results
                        .stream()
                        .filter(findResult ->
                                !ExplorerConstants.FAVOURITES_NODE.getName().equals(findResult.getPath()))
                        .collect(Collectors.toMap(FindResult::getDocRef, Function.identity()));
                final List<FindResult> recentItems = request
                        .getFilter().getRecentItems()
                        .stream()
                        .map(resultMap::get)
                        .filter(Objects::nonNull)
                        .toList();
                return ResultPage.createPageLimitedList(recentItems, request.getPageRequest());
            } else {
                results.sort(Comparator
                        .<FindResult, String>comparing(res -> res.getDocRef().getName(), Comparator.naturalOrder())
                        .thenComparing(FindResult::getPath)
                        .thenComparing(res -> res.getDocRef().getType())
                        .thenComparing(res -> res.getDocRef().getUuid()));
            }

            return ResultPage.createPageLimitedList(results, request.getPageRequest());

        } catch (Exception e) {
            LOGGER.error("Error finding nodes with request {}", request, e);
            throw e;
        }
    }

    private void addResults(final String parent,
                            final List<ExplorerNode> nodes,
                            final List<FindResult> results) {
        if (nodes != null) {
            for (final ExplorerNode node : nodes) {
                if (node.hasNodeFlag(NodeFlag.FILTER_MATCH) &&
                        node.getDocRef() != null &&
                        !Objects.equals(ExplorerConstants.SYSTEM, node.getType()) &&
                        !Objects.equals(ExplorerConstants.FAVOURITES, node.getType())) {
                    results.add(new FindResult(
                            node.getDocRef(),
                            parent,
                            node.getIcon()));
                }
                addResults(
                        parent.isEmpty()
                                ? node.getName()
                                : parent + " / " + node.getName(),
                        node.getChildren(),
                        results);
            }
        }
    }

    @Override
    public ResultPage<FindInContentResult> findInContent(final FindInContentRequest request) {
        final List<FindInContentResult> list = new ArrayList<>();
        for (final DocumentType documentType : explorerActionHandlers.getTypes()) {
            final ExplorerActionHandler explorerActionHandler =
                    explorerActionHandlers.getHandler(documentType.getType());
            final List<DocContentMatch> matches = explorerActionHandler.findByContent(request.getFilter());
            for (final DocContentMatch docContentMatch : matches) {
                final List<String> parents = new ArrayList<>();
                parents.add(docContentMatch.getDocRef().getName());
                final UnmodifiableTreeModel masterTreeModel = explorerTreeModel.getModel();
                if (masterTreeModel != null) {
                    ExplorerNode parent = masterTreeModel.getParent(ExplorerNode
                            .builder()
                            .docRef(docContentMatch.getDocRef())
                            .build());
                    while (parent != null) {
                        parents.add(parent.getName());
                        parent = masterTreeModel.getParent(parent);
                    }
                }
                final StringBuilder parentPath = new StringBuilder();
                for (int i = parents.size() - 1; i >= 0; i--) {
                    String parent = parents.get(i);
                    parentPath.append(parent);
                    if (i > 0) {
                        parentPath.append(" / ");
                    }
                }

                final FindInContentResult explorerDocContentMatch = FindInContentResult.builder()
                        .docContentMatch(docContentMatch)
                        .path(parentPath.toString())
                        .icon(explorerActionHandler.getDocumentType().getIcon())
                        .build();
                list.add(explorerDocContentMatch);
            }
        }

        final PageRequest pageRequest = request.getPageRequest();
        return ResultPage.createPageLimitedList(list, pageRequest);
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        final ExplorerActionHandler explorerActionHandler =
                explorerActionHandlers.getHandler(request.getDocRef().getType());
        return explorerActionHandler.fetchHighlights(request.getDocRef(), request.getExtension(), request.getFilter());
    }

    @Override
    public Set<String> parseNodeTags(final String tagsStr) {
        return NodeTagSerialiser.deserialise(tagsStr);
    }

    @Override
    public String nodeTagsToString(final Set<String> tags) {
        return NodeTagSerialiser.serialise(tags);
    }

    @Override
    public Suggestions getSuggestions(final FetchSuggestionsRequest request) {
        return securityContext.secureResult(() -> {
            if (ExplorerFields.TAG.getFldName().equals(request.getField().getFldName())) {
                final Set<String> tags = getTags();
                return new Suggestions(new ArrayList<>(tags), false);
            } else {
                throw new RuntimeException(LogUtil.message("Unexpected field " + request.getField().getFldName()));
            }
        });
    }

    // --------------------------------------------------------------------------------


    /**
     * @param hasChildren         Whether this node has any children that are to be included in the tree.
     * @param hasIssues           Whether this node or any of its descendants have an issue.
     * @param isFolder            Whether this node is a folder, i.e. has children that may or may not be included
     *                            in the tree.
     * @param containsFilterMatch Whether this node or any descendant is a filter match.
     */
    private record NodeStates(boolean hasChildren,
                              boolean hasIssues,
                              boolean isFolder,
                              boolean containsFilterMatch,
                              Set<ExplorerNodeKey> openNodes) {

    }


    // --------------------------------------------------------------------------------


    private enum TagOperation {
        ADD,
        REMOVE
    }
}
