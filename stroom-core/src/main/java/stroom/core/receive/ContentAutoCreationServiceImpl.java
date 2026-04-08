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

package stroom.core.receive;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.DataFormatNames;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.UnauthenticatedUserIdentity;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.security.api.AppPermissionService;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.User;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.DocPath;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserType;
import stroom.util.shared.string.CIKey;
import stroom.util.shared.string.CaseType;
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Template;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class ContentAutoCreationServiceImpl implements ContentAutoCreationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentAutoCreationServiceImpl.class);
    private static final String LOCK_NAME = "AUTO_CONTENT_CREATION";
    private static final Pattern PATH_PARAM_REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9 _-]");
    private static final Pattern PATH_STATIC_REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9 /_-]");
    private static final Pattern GROUP_REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9-]");
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(1);
    private static final Set<String> COPYABLE_DOC_TYPES = Set.of(
            XsltDoc.TYPE,
            TextConverterDoc.TYPE);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider;
    private final DocumentPermissionService documentPermissionService;
    private final AppPermissionService appPermissionService;
    private final UserService userService;
    private final FeedStore feedStore;
    private final ExplorerService explorerService;
    private final ExplorerNodeService explorerNodeService;
    private final ClusterLockService clusterLockService;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final ContentTemplateStore contentTemplateStore;
    private final ProcessorFilterService processorFilterService;
    private final PipelineService pipelineService;
    private final CachedValue<ExpressionMatcher, Set<String>> cachedExpressionMatcher;
    private final CachedValue<Template, String> cachedDestinationPathTemplator;
    private final CachedValue<Template, String> cachedDestinationSubPathTemplator;
    private final CachedValue<Template, String> cachedGroupTemplator;
    private final CachedValue<Template, String> cachedAdditionalGroupTemplator;

    @Inject
    public ContentAutoCreationServiceImpl(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                          final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider,
                                          final DocumentPermissionService documentPermissionService,
                                          final AppPermissionService appPermissionService,
                                          final UserService userService,
                                          final FeedStore feedStore,
                                          final ExplorerService explorerService,
                                          final ExplorerNodeService explorerNodeService,
                                          final ClusterLockService clusterLockService,
                                          final MetaService metaService,
                                          final SecurityContext securityContext,
                                          final ContentTemplateStore contentTemplateStore,
                                          final ProcessorFilterService processorFilterService,
                                          final PipelineService pipelineService,
                                          final ExpressionMatcherFactory expressionMatcherFactory,
                                          final ExpressionPredicateFactory expressionPredicateFactory) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.autoContentCreationConfigProvider = autoContentCreationConfigProvider;
        this.documentPermissionService = documentPermissionService;
        this.appPermissionService = appPermissionService;
        this.userService = userService;
        this.feedStore = feedStore;
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.clusterLockService = clusterLockService;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.contentTemplateStore = contentTemplateStore;
        this.processorFilterService = processorFilterService;
        this.pipelineService = pipelineService;

        // TODO change to use ExpressionPredicateFactory
        this.cachedExpressionMatcher = CachedValue.builder()
                .withMaxCheckInterval(CHECK_INTERVAL)
                .withStateSupplier(() ->
                        autoContentCreationConfigProvider.get().getTemplateMatchFields())
                .withValueFunction(templateMatchFields ->
                        createExpressionMatcher(expressionMatcherFactory, templateMatchFields))
                .build();
        this.cachedDestinationPathTemplator = CachedValue.builder()
                .withMaxCheckInterval(CHECK_INTERVAL)
                .withStateSupplier(() ->
                        autoContentCreationConfigProvider.get().getDestinationExplorerPathTemplate())
                .withValueFunction(template ->
                        TemplateUtil.parseTemplate(
                                template,
                                ContentAutoCreationServiceImpl::cleanTemplateVariable,
                                ContentAutoCreationServiceImpl::cleanTemplateStaticText))
                .build();
        this.cachedDestinationSubPathTemplator = CachedValue.builder()
                .withMaxCheckInterval(CHECK_INTERVAL)
                .withStateSupplier(() ->
                        autoContentCreationConfigProvider.get().getDestinationExplorerSubPathTemplate())
                .withValueFunction(template ->
                        TemplateUtil.parseTemplate(
                                template,
                                ContentAutoCreationServiceImpl::cleanTemplateVariable,
                                ContentAutoCreationServiceImpl::cleanTemplateStaticText))
                .build();
        this.cachedGroupTemplator = CachedValue.builder()
                .withMaxCheckInterval(CHECK_INTERVAL)
                .withStateSupplier(() ->
                        autoContentCreationConfigProvider.get().getGroupTemplate())
                .withValueFunction(template -> TemplateUtil.parseTemplate(
                        template,
                        ContentAutoCreationServiceImpl::cleanGroupString))
                .build();
        this.cachedAdditionalGroupTemplator = CachedValue.builder()
                .withMaxCheckInterval(CHECK_INTERVAL)
                .withStateSupplier(() ->
                        autoContentCreationConfigProvider.get().getAdditionalGroupTemplate())
                .withValueFunction(template -> TemplateUtil.parseTemplate(
                        template,
                        ContentAutoCreationServiceImpl::cleanGroupString))
                .build();
    }

    private static String cleanGroupString(final String group) {
        return GROUP_REPLACE_PATTERN.matcher(NullSafe.trim(group))
                .replaceAll("-");
    }

    private static ExpressionMatcher createExpressionMatcher(final ExpressionMatcherFactory expressionMatcherFactory,
                                                             final Set<String> templateMatchFields) {
        // ExpressionMatcher is currently case-sensitive so normalise to lower case
        final Map<String, QueryField> fields = NullSafe.stream(templateMatchFields)
                .filter(NullSafe::isNonBlankString)
                .map(ContentAutoCreationServiceImpl::normaliseField)
                .collect(Collectors.toMap(Function.identity(), QueryField::createText));
        return expressionMatcherFactory.create(fields);
    }

    private static String cleanTemplateVariable(String str) {
        return PATH_PARAM_REPLACE_PATTERN.matcher(NullSafe.trim(str))
                .replaceAll("_");
    }

    private static String cleanTemplateStaticText(String str) {
        return PATH_STATIC_REPLACE_PATTERN.matcher(NullSafe.trim(str))
                .replaceAll("_");
    }

    @Override
    public Optional<FeedDoc> tryCreateFeed(final String feedName,
                                           final UserDesc userDesc,
                                           final AttributeMap attributeMap) {
        LOGGER.debug("tryCreateFeed - feedName: {}, userRef: {}, attributeMap: {}",
                feedName, userDesc, attributeMap);

        // If the feed exists we assume that either auto creation has happened or
        // this feed is not subject to auto creation. Either way we don't bother
        // trying to match on the templates
        Optional<FeedDoc> optFeedDoc = Optional.empty();
        if (NullSafe.isNonBlankString(feedName)) {
            // Should only ever be one
            optFeedDoc = NullSafe.stream(feedStore.findByName(feedName))
                    .findFirst()
                    .map(feedStore::readDocument);
            LOGGER.debug("tryCreateFeed - feedName: {}, feedDoc: {}",
                    feedName, optFeedDoc);
        }

        if (optFeedDoc.isEmpty()) {
            optFeedDoc = getMatchingTemplate(attributeMap)
                    .flatMap(contentTemplate -> {
                        try {
                            // Content gets created as the configured user
                            final UserRef runAsUserRef = getRunAsUser();

                            final Optional<FeedDoc> optFeedDoc2 = securityContext.asUserResult(runAsUserRef, () ->
                                    ensureFeed(feedName, userDesc, attributeMap, contentTemplate));

                            LOGGER.debug("feedName: '{}', userDesc: '{}', optFeedDoc: {}",
                                    feedName, userDesc, optFeedDoc2);
                            return optFeedDoc2;
                        } catch (final Exception e) {
                            LOGGER.error("Error applying contentTemplate {} - {}",
                                    contentTemplate, LogUtil.exceptionMessage(e), e);
                            throw e;
                        }
                    });
        }
        return optFeedDoc;
    }

    private UserRef getRunAsUser() {
        final AutoContentCreationConfig autoContentCreationConfig = autoContentCreationConfigProvider.get();
        final String createAsSubjectId = Objects.requireNonNull(autoContentCreationConfig.getCreateAsSubjectId());
        final UserType createAsType = Objects.requireNonNull(autoContentCreationConfig.getCreateAsType());

        return switch (createAsType) {
            case USER -> userService.getUserBySubjectId(createAsSubjectId)
                    .map(User::asRef)
                    .orElseThrow(() -> new RuntimeException(LogUtil.message(
                            "No user found with subjectId equal to createAsSubjectId '{}'",
                            createAsSubjectId)));
            case GROUP -> userService.getGroupByName(createAsSubjectId)
                    .map(User::asRef)
                    .orElseThrow(() -> new RuntimeException(LogUtil.message(
                            "No group found with name equal to createAsSubjectId '{}'",
                            createAsSubjectId)));
        };
    }

    private Optional<FeedDoc> ensureFeed(final String feedName,
                                         final UserDesc userDesc,
                                         final AttributeMap attributeMap,
                                         final ContentTemplate contentTemplate) {

        // We will only come in here if the caller thought the feed needed creating
        // so the lock won't impact normal running once the feed is set up.
        // Only one lock for all auto-created feeds to save us creating one lock per
        // feed. Feeds won't be auto-created very often so contention is unlikely.
        final DurationTimer timer = DurationTimer.start();
        final DocRef feedDocRef = clusterLockService.lockResult(LOCK_NAME, () -> {
            LOGGER.debug("Waited {} to obtain lock", timer);
            // Re-test under lock
            DocRef docRef;
            final List<DocRef> feeds = feedStore.findByName(feedName);
            if (feeds.isEmpty()) {
                try {
                    docRef = createFeedAndContent(feedName, userDesc, attributeMap, contentTemplate);
                } catch (final EntityServiceException e) {
                    // It's possible that another thread/node has created the feed
                    if (NullSafe.containsIgnoringCase(e.getMessage(), "exists")) {
                        // Feeds have unique names, so get first
                        docRef = feedStore.findByName(feedName)
                                .get(0);
                    } else {
                        throw e;
                    }
                }
                return docRef;
            } else {
                // Feeds have unique name so get first
                docRef = feeds.get(0);
            }
            return docRef;
        });
        LOGGER.debug("Released lock after {}", timer);
        return Optional.of(feedStore.readDocument(feedDocRef));
    }

    private DocRef createFeedAndContent(final String feedName,
                                        final UserDesc userDesc,
                                        final AttributeMap attributeMap,
                                        final ContentTemplate contentTemplate) {

        final AutoContentCreationConfig autoContentCreationConfig = autoContentCreationConfigProvider.get();
        final Template pathTemplator = cachedDestinationPathTemplator.getValue();
        final Map<CIKey, String> caseInsenseAttrMap = CIKey.mapOf(attributeMap);
        final String destinationPath = pathTemplator.executeWith(caseInsenseAttrMap);
        final DocPath baseDocPath = DocPath.fromPathString(destinationPath);

        final ExplorerNode destFolder = ensureExplorerNode(baseDocPath);
        final DocRef destFolderRef = destFolder.getDocRef();

        // Only create a sub dir if there are some deps to put in it
        final Optional<ExplorerNode> optDestSubFolder;
        if (contentTemplate.isCopyElementDependencies()) {
            // If a sub dir has been configured then ensure it exists
            final Template subPathTemplator = cachedDestinationSubPathTemplator.getValue();
            if (!subPathTemplator.isBlank()) {
                final DocPath subDirDocPath = baseDocPath.append(DocPath.fromPathString(
                        subPathTemplator.executeWith(caseInsenseAttrMap)));
                optDestSubFolder = Optional.ofNullable(
                        ensureExplorerNode(subDirDocPath));
            } else {
                optDestSubFolder = Optional.empty();
            }
        } else {
            optDestSubFolder = Optional.empty();
        }

        final UserRef userRef;
        // Get/create the user if possible
        if (userDesc != null) {
            if (UnauthenticatedUserIdentity.getInstance().subjectId().equals(userDesc.getSubjectId())) {
                LOGGER.debug("Unauthenticated user {}", userDesc);
                userRef = null;
            } else {
                LOGGER.info("Ensuing user with userRef: '{}' exists", userDesc);
                final User user = userService.getOrCreateUser(userDesc);
                userRef = user.asRef();
            }
        } else {
            LOGGER.info("No user details, won't ensure Stroom user or add any users to groups.");
            userRef = null;
        }

        // Set up the group
        final Template groupTemplator = cachedGroupTemplator.getValue();
        final User group = ensureGroup(groupTemplator, caseInsenseAttrMap, userRef);
        final String groupParentGroupName = autoContentCreationConfig.getGroupParentGroupName();
        if (NullSafe.isNonBlankString(groupParentGroupName)) {
            // Ensure the common parent group for the main group
            ensureGroup(groupParentGroupName, group.asRef());
        }

        // Additional group only needed if we are copying deps, else it would be
        // the same as the main group
        final Optional<User> optAdditionalGroup;
        if (contentTemplate.isCopyElementDependencies()) {
            final Template additionalGroupTemplator = cachedAdditionalGroupTemplator.getValue();
            optAdditionalGroup = Optional.ofNullable(
                    ensureGroup(additionalGroupTemplator, caseInsenseAttrMap, userRef));
            optAdditionalGroup.ifPresent(additionalGroup -> {
                final String additionalGroupParentGroupName =
                        autoContentCreationConfig.getAdditionalGroupParentGroupName();
                if (NullSafe.isNonBlankString(additionalGroupParentGroupName)) {
                    // Ensure the common parent group for the additional group
                    ensureGroup(additionalGroupParentGroupName, additionalGroup.asRef());
                }
            });
        } else {
            optAdditionalGroup = Optional.empty();
        }

        final FeedDoc feedDoc = createFeedDoc(feedName, attributeMap, baseDocPath, destFolder, userRef);
        final DocRef feedDocRef = feedDoc.asDocRef();

        grantPermOnDoc(destFolderRef, group, DocumentPermission.VIEW);
        grantPermOnDoc(feedDocRef, group, DocumentPermission.VIEW);

        optAdditionalGroup.ifPresent(additionalGroup -> {
            // Give the group EDIT on the feed
            grantPermOnDoc(feedDocRef, additionalGroup, DocumentPermission.VIEW);
            if (optDestSubFolder.isPresent()) {
                final ExplorerNode destSubFolder = optDestSubFolder.get();
                grantPermOnDoc(destFolderRef, additionalGroup, DocumentPermission.VIEW);
                grantPermOnDoc(destSubFolder.getDocRef(), group, DocumentPermission.VIEW);
                grantPermOnDoc(destSubFolder.getDocRef(), additionalGroup, DocumentPermission.EDIT);
            } else {
                grantPermOnDoc(destFolderRef, additionalGroup, DocumentPermission.EDIT);
            }
        });

        // Create any templated content
        final Optional<DocRef> optNewPipeDocRef = createTemplatedContent(
                attributeMap,
                feedDocRef,
                destFolder,
                optDestSubFolder,
                contentTemplate);

        optNewPipeDocRef.ifPresent(newPipeDocRef -> {
            grantPermOnDoc(newPipeDocRef, group, DocumentPermission.VIEW);
            optAdditionalGroup.ifPresent(additionalGroup -> {
                grantPermOnDoc(newPipeDocRef, additionalGroup, DocumentPermission.VIEW);
            });
        });

        LOGGER.debug("feedDoc after configuration: {}", feedDoc);
        return feedDocRef;
    }

    private FeedDoc createFeedDoc(final String feedName,
                                  final AttributeMap attributeMap,
                                  final DocPath baseDocPath,
                                  final ExplorerNode destFolder,
                                  final UserRef userRef) {
        // Creates the explorer node for the Feed and the FeedDoc itself
        LOGGER.info("Auto-creating feed {} in path '{}'", feedName, baseDocPath);
        final DocRef feedDocRef = explorerService.create(
                FeedDoc.TYPE,
                feedName,
                destFolder,
                PermissionInheritance.DESTINATION).getDocRef();

        FeedDoc feedDoc = feedStore.readDocument(feedDocRef);
        // Set up the feed doc using the information in the data feed key
        feedDoc = configureFeed(feedDoc, attributeMap, userRef);
        feedDoc = feedStore.writeDocument(feedDoc);
        return feedDoc;
    }

    private ExplorerNode ensureExplorerNode(final DocPath docPath) {
        LOGGER.info("Ensuring explorer path '{}' exists", docPath);
        return explorerService.ensureFolderPath(docPath, PermissionInheritance.DESTINATION);
    }

    private FeedDoc configureFeed(final FeedDoc feedDoc,
                                  final AttributeMap attributeMap,
                                  final UserRef userRef) {
        final FeedDoc.Builder builder = feedDoc.copy();
        if (NullSafe.hasEntries(attributeMap)) {
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

            // By this point all the entries in DataFeedKey.streamMetaData have been set in attributeMap
            // so we can just use that.
            consumeAttrVal(attributeMap, StandardHeaderArguments.TYPE, type -> {
                if (NullSafe.set(receiveDataConfig.getMetaTypes()).contains(type)) {
                    builder.streamType(type);
                }
            });

            if (userRef != null) {
                builder.description("Auto-created for user '" + userRef.toDisplayString() + "'");
            } else {
                builder.description("Auto-created");
            }
            builder.status(FeedStatus.RECEIVE);

            consumeAttrVal(attributeMap, StandardHeaderArguments.ENCODING, val ->
                    builder.encoding(getEncoding(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.CONTEXT_ENCODING, val ->
                    builder.contextEncoding(getEncoding(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.CLASSIFICATION, builder::classification);
            consumeAttrVal(attributeMap, StandardHeaderArguments.FORMAT, val ->
                    builder.dataFormat(getFormat(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.CONTEXT_FORMAT, val ->
                    builder.contextFormat(getFormat(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.SCHEMA, builder::schema);
            consumeAttrVal(attributeMap, StandardHeaderArguments.SCHEMA_VERSION, builder::schemaVersion);
        }
        return builder.build();
    }

    private String getFormat(final String value, final FeedDoc feedDoc) {
        if (NullSafe.isBlankString(value)) {
            return null;
        } else {
            final Set<String> dataFormats = metaService.getDataFormats();
            String format = value.toUpperCase();
            if (dataFormats.contains(format)) {
                return format;
            } else {
                // Default to TEXT if the value is not a known one
                format = DataFormatNames.TEXT;
                LOGGER.warn("Unknown data format name '{}' when auto-creating feed '{}', using '{}' instead",
                        value, feedDoc.getName(), format);
                return format;
            }
        }
    }

    private String getEncoding(final String value, final FeedDoc feedDoc) {
        if (NullSafe.isBlankString(value)) {
            return null;
        } else {
            final List<String> supportedEncodings = feedStore.fetchSupportedEncodings();
            // If encoding is not supplied it will get defaulted to utf8 on use
            final String encoding = supportedEncodings.stream()
                    .filter(value::equalsIgnoreCase)
                    .findFirst()
                    .orElse(null);

            if (encoding == null) {
                LOGGER.warn("Unknown encoding '{}' when auto-creating feed '{}', setting encoding to blank instead",
                        value, feedDoc.getName());
            }
            return encoding;
        }
    }

    private void consumeAttrVal(final AttributeMap attributeMap,
                                final String key,
                                final Consumer<String> valueConsumer) {
        if (attributeMap.containsKey(key)) {
            final String val = attributeMap.get(key);
            if (valueConsumer != null) {
                valueConsumer.accept(val);
            }
        }
    }

    private void grantPermOnDoc(final DocRef docRef,
                                final User user,
                                final DocumentPermission perm) {
        LOGGER.info(() -> LogUtil.message("Granting permission {} on {} to {} {}",
                perm, docRef, user.getType(CaseType.LOWER), user.getUserRef().toDisplayString()));
        documentPermissionService.setPermission(docRef, user.asRef(), perm);
    }

    private Optional<ContentTemplate> getMatchingTemplate(final AttributeMap attributeMap) {

        final ContentTemplates contentTemplates = contentTemplateStore.getOrCreate();
        final List<ContentTemplate> activeTemplates = contentTemplates.getActiveTemplates();
        ContentTemplate matchingTemplate = null;
        Map<String, Object> normalisedAttributes = null;
        if (NullSafe.hasItems(activeTemplates)) {
            for (final ContentTemplate contentTemplate : activeTemplates) {
                final ExpressionOperator expression = contentTemplate.getExpression();
                if (expression == null) {
                    matchingTemplate = contentTemplate;
                    break;
                } else {
                    if (normalisedAttributes == null) {
                        // Normalise the keys to lower case
                        normalisedAttributes = attributeMap.asMap(true)
                                .entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                        entry1 -> normaliseField(entry1.getKey()),
                                        entry -> NullSafe.get(
                                                entry.getValue(),
                                                val -> (Object) val)));
                    }

                    final boolean isMatch = cachedExpressionMatcher.getValue()
                            .match(normalisedAttributes, expression);
                    if (isMatch) {
                        matchingTemplate = contentTemplate;
                        break;
                    }
                }
            }
        }
        if (LOGGER.isInfoEnabled()) {
            if (matchingTemplate != null) {
                LOGGER.info("Data matched content template {} '{}', attributeMap: {}",
                        matchingTemplate.getTemplateNumber(), matchingTemplate.getName(), attributeMap);
            } else {
                LOGGER.info("Data didn't match any active content templates, attributeMap: {}", attributeMap);
            }
        }
        return Optional.ofNullable(matchingTemplate);
    }

    private static String normaliseField(final String field) {
        return NullSafe.get(
                field,
                String::trim,
                String::toLowerCase);
    }

    private Optional<DocRef> createTemplatedContent(final AttributeMap attributeMap,
                                                    final DocRef feedDocRef,
                                                    final ExplorerNode destFolder,
                                                    final Optional<ExplorerNode> optDestSubFolder,
                                                    final ContentTemplate contentTemplate) {

        LOGGER.debug("createTemplatedContent() - Matched template {}, attributeMap: {}, " +
                     "destFolder: {}, destSubFolder: {}",
                contentTemplate, attributeMap, destFolder, optDestSubFolder);
        final DocRef pipelineDocRef = Objects.requireNonNull(contentTemplate.getPipeline());
        Objects.requireNonNull(pipelineDocRef,
                () -> LogUtil.message("No pipeline defined in contentTemplate {}", contentTemplate));
        final PipelineDoc pipelineDoc;
        try {
            pipelineDoc = pipelineService.fetch(pipelineDocRef.getUuid());
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Unable to fetch the pipeline {} configured in content template {} '{}'.",
                    pipelineDocRef,
                    contentTemplate.getTemplateNumber(),
                    contentTemplate.getName()), e);
        }

        final DocRef newPipeDocRef = switch (contentTemplate.getTemplateType()) {

            case PROCESSOR_FILTER -> {
                createProcessorFilter(
                        attributeMap.get(StandardHeaderArguments.TYPE),
                        contentTemplate.getPipeline(),
                        feedDocRef,
                        contentTemplate);
                // No new pipe created.
                yield null;
            }

            case INHERIT_PIPELINE -> createPipelineFromParent(
                    pipelineDoc,
                    attributeMap.get(StandardHeaderArguments.TYPE),
                    feedDocRef,
                    destFolder,
                    optDestSubFolder,
                    contentTemplate);
        };
        LOGGER.debug("createTemplatedContent() - Returning newPipeDocRef: {}", newPipeDocRef);
        return Optional.ofNullable(newPipeDocRef);
    }

    private void createProcessorFilter(final String streamType,
                                       final DocRef pipelineDocRef,
                                       final DocRef feedDocRef,
                                       final ContentTemplate contentTemplate) {
        try {
            final String type = NullSafe.nonBlankStringElse(streamType, StreamTypeNames.RAW_EVENTS);

            final ExpressionOperator expression = ExpressionOperator.builder()
                    .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, feedDocRef)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, type)
                    .build();
            // We are currently running as the user defined in config
            final UserRef runAsUser = securityContext.getUserRef();
            final CreateProcessFilterRequest request = CreateProcessFilterRequest.builder()
                    .queryData(QueryData.builder()
                            .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                            .expression(expression)
                            .build())
                    .pipeline(pipelineDocRef)
                    .processorType(ProcessorType.PIPELINE)
                    .priority(contentTemplate.getProcessorPriority())
                    .autoPriority(false)
                    .maxProcessingTasks(contentTemplate.getProcessorMaxConcurrent())
                    .enabled(true)
                    .runAsUser(runAsUser)
                    .build();

            processorFilterService.create(request);

            LOGGER.info(() -> LogUtil.message(
                    "Created processor filter using contentTemplate {} '{}' for expression: {}, running as {}",
                    contentTemplate.getTemplateNumber(), contentTemplate.getName(), expression, runAsUser));
        } catch (final Exception e) {
            LOGGER.error("Error creating processor filter on {}, contentTemplate: {}, feedDocRef: {}, {}",
                    pipelineDocRef, contentTemplate, feedDocRef, LogUtil.exceptionMessage(e), e);
            throw new RuntimeException(e);
        }
    }

    private DocRef createPipelineFromParent(final PipelineDoc parentPipelineDoc,
                                            final String streamType,
                                            final DocRef feedDocRef,
                                            final ExplorerNode destFolder,
                                            final Optional<ExplorerNode> optDestSubFolder,
                                            final ContentTemplate contentTemplate) {

        DocRef parentPipeDocRef = null;
        try {
            parentPipeDocRef = Objects.requireNonNull(parentPipelineDoc).asDocRef();
            LOGGER.debug("createPipelineFromParent() - parentPipelineDoc: {}, feedDocRef: {}, " +
                         "destFolder: {}, destSubFolder: {}, contentTemplate: {}",
                    parentPipeDocRef, feedDocRef, destFolder, optDestSubFolder, contentTemplate);

            // Use feed name for the name of the new pipeline
            final String pipeDocName = feedDocRef.getName();
            final ExplorerNode newPipelineNode = explorerService.create(
                    PipelineDoc.TYPE,
                    pipeDocName,
                    destFolder,
                    PermissionInheritance.DESTINATION);

            // Update the new pipe so it inherits from the parent
            final String newPipelineUuid = newPipelineNode.getUuid();
            PipelineDoc newPipelineDoc = pipelineService.fetch(newPipelineUuid);
            final DocRef newPipelineDocRef = newPipelineDoc.asDocRef();
            newPipelineDoc = newPipelineDoc.copy()
                    .parentPipeline(parentPipeDocRef)
                    .build();

            if (contentTemplate.isCopyElementDependencies()) {
                newPipelineDoc = copyPipelineElementDependencies(
                        parentPipelineDoc, destFolder, optDestSubFolder, newPipelineDoc);
            }
            pipelineService.update(newPipelineUuid, newPipelineDoc);

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Created pipeline {} with parentPipeline {} using contentTemplate '{}'",
                        newPipelineDocRef, parentPipeDocRef, contentTemplate.getName());
            }

            // Now create the proc filter for the new pipe
            createProcessorFilter(streamType, newPipelineDocRef, feedDocRef, contentTemplate);
            return newPipelineDocRef;
        } catch (final RuntimeException e) {
            LOGGER.error("Error creating pipeline that inherits {}, contentTemplate: {}, feedDocRef: {}, {}",
                    parentPipeDocRef, contentTemplate, feedDocRef, LogUtil.exceptionMessage(e), e);
            throw new RuntimeException(e);
        }
    }

    private PipelineDoc copyPipelineElementDependencies(final PipelineDoc parentPipelineDoc,
                                                        final ExplorerNode destFolder,
                                                        final Optional<ExplorerNode> optDestSubFolder,
                                                        final PipelineDoc newPipelineDoc) {
        final Set<PipelineProperty> directEntityDependencies = getDirectEntityDependencies(parentPipelineDoc);
        LOGGER.debug(
                "copyPipelineElementDependencies() - directEntityDependencies: {}, destFolder: {}, destSubFolder: {}",
                directEntityDependencies,
                destFolder,
                optDestSubFolder);
        final ExplorerNode effectiveDestination = optDestSubFolder.orElse(destFolder);
        LOGGER.debug("copyPipelineElementDependencies() - effectiveDestination: {}", effectiveDestination);
        Objects.requireNonNull(effectiveDestination);
        if (NullSafe.hasItems(directEntityDependencies)) {
            final Map<DocRef, DocRef> remappings = new HashMap<>(directEntityDependencies.size());
            for (final PipelineProperty property : directEntityDependencies) {
                final DocRef depDocRef = property.getValue().getEntity();
                final ExplorerNode dependencyNode = explorerNodeService.getNode(depDocRef)
                        .orElseThrow(() ->
                                new RuntimeException("No explorer node found for " + property));
                final String newName = newPipelineDoc.getName() + "-" + property.getElement();
                final BulkActionResult result = explorerService.copy(
                        List.of(dependencyNode),
                        effectiveDestination,
                        true,
                        newName,
                        PermissionInheritance.DESTINATION);
                if (NullSafe.size(result.getExplorerNodes()) != 1) {
                    throw new RuntimeException("Expecting exactly one node");
                }
                final ExplorerNode nodeCopy = result.getExplorerNodes().getFirst();
                LOGGER.debug(() -> LogUtil.message("createPipelineFromParent() - Copied: {} to: {}",
                        dependencyNode.getDocRef(), nodeCopy.getDocRef()));
                remappings.put(dependencyNode.getDocRef(), nodeCopy.getDocRef());
            }
            // Update the pipeline so it uses
            final List<PipelineProperty> parentAddedProperties = parentPipelineDoc.getPipelineData()
                    .getAddedProperties();
            final PipelineDataBuilder pipelineDataBuilder = new PipelineDataBuilder();
            for (final PipelineProperty parentAddedProperty : parentAddedProperties) {
                final DocRef propEntity = NullSafe.get(
                        parentAddedProperty,
                        PipelineProperty::getValue,
                        PipelinePropertyValue::getEntity);

                final DocRef newPropEntity = remappings.get(propEntity);
                if (newPropEntity != null) {
                    final PipelineProperty newPipelineProperty = PipelineProperty.builder(parentAddedProperty)
                            .value(new PipelinePropertyValue(newPropEntity))
                            .build();
                    pipelineDataBuilder.addProperty(newPipelineProperty);
                }
            }
            return newPipelineDoc.copy()
                    .pipelineData(pipelineDataBuilder.build())
                    .build();
        } else {
            LOGGER.debug("copyPipelineElementDependencies() - No direct dependencies");
            return newPipelineDoc;
        }
    }

    private Set<PipelineProperty> getDirectEntityDependencies(final PipelineDoc pipelineDoc) {
        final Set<PipelineProperty> elmDepDocRefs = pipelineDoc.getPipelineData()
                .getAddedProperties()
                .stream()
                .filter(prop -> {
                    final DocRef docRef = NullSafe.get(
                            prop,
                            PipelineProperty::getValue,
                            PipelinePropertyValue::getEntity);
                    return isTypeSuitableForCopy(docRef);
                })
                .collect(Collectors.toSet());

        LOGGER.debug("fetchDirectElementDependencies() - elmDepDocRefs: {}", elmDepDocRefs);
        return elmDepDocRefs;
    }

    /**
     * We don't want to copy feed/indexes
     */
    private boolean isTypeSuitableForCopy(final DocRef docRef) {
        final String type = NullSafe.get(docRef, DocRef::getType);
        return type != null
               && COPYABLE_DOC_TYPES.contains(type);
    }

    private User ensureGroup(final Template groupNameTemplator,
                             final Map<CIKey, String> caseInsenseAttrMap,
                             final UserRef... groupMembers) {
        final String groupName = groupNameTemplator.executeWith(caseInsenseAttrMap);
        LOGGER.debug("ensureGroup() - groupNameTemplator: {}, groupName: {}, groupMembers: {}, caseInsenseAttrMap: {}",
                groupNameTemplator, groupName, groupMembers, caseInsenseAttrMap);
        return ensureGroup(groupName, groupMembers);
    }

    private User ensureGroup(final String groupName, final UserRef... groupMembers) {
        LOGGER.debug("ensureGroup() - groupName: {}, groupMembers: {}", groupName, groupMembers);
        if (NullSafe.isNonBlankString(groupName)) {
            LOGGER.info("Auto-creating user group '{}'", groupName);
            final User group = userService.getOrCreateUserGroup(groupName);
            NullSafe.forEach(groupMembers, groupMember -> {
                if (groupMember != null) {
                    LOGGER.info("Adding userRef {} of type {} to group '{}",
                            groupMember, groupMember.getType(), groupName);
                    userService.addUserToGroup(groupMember, group.asRef());
                }
            });
            return group;
        } else {
            LOGGER.debug(() -> LogUtil.message("ensureGroup() - groupName is blank, groupMembers: {}",
                    (Object[]) groupMembers));
            return null;
        }
    }
}
