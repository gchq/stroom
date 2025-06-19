package stroom.core.receive;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
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
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.security.api.AppPermissionService;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.AppPermission;
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
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Templator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

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
    private final CachedValue<Templator, String> cachedDestinationPathTemplator;
    private final CachedValue<Templator, String> cachedGroupTemplator;
    private final CachedValue<Templator, String> cachedAdditionalGroupTemplator;

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
                                          final ExpressionMatcherFactory expressionMatcherFactory) {
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
        this.cachedExpressionMatcher = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() ->
                        autoContentCreationConfigProvider.get().getTemplateMatchFields())
                .withValueFunction(templateMatchFields ->
                        createExpressionMatcher(expressionMatcherFactory, templateMatchFields))
                .build();
        this.cachedDestinationPathTemplator = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() -> autoContentCreationConfigProvider.get().getDestinationExplorerPathTemplate())
                .withValueFunction(template -> TemplateUtil.parseTemplate(
                        template,
                        str -> PATH_PARAM_REPLACE_PATTERN.matcher(NullSafe.trim(str)).replaceAll("_"),
                        str -> PATH_STATIC_REPLACE_PATTERN.matcher(NullSafe.trim(str)).replaceAll("_")
                ))
                .build();
        this.cachedGroupTemplator = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() -> autoContentCreationConfigProvider.get().getGroupTemplate())
                .withValueFunction(template -> TemplateUtil.parseTemplate(
                        template,
                        ContentAutoCreationServiceImpl::cleanGroupString))
                .build();
        this.cachedAdditionalGroupTemplator = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() -> autoContentCreationConfigProvider.get().getAdditionalGroupTemplate())
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

    @Override
    public Optional<FeedDoc> tryCreateFeed(final String feedName,
                                           final UserDesc userDesc,
                                           final AttributeMap attributeMap) {
        LOGGER.debug("tryCreateFeed - feedName: {}, userRef: {}, attributeMap: {}",
                feedName, userDesc, attributeMap);

        // Content gets created as the configured user
        final UserRef runAsUserRef = getRunAsUser();

        final Optional<FeedDoc> optFeedDoc = securityContext.asUserResult(runAsUserRef, () ->
                ensureFeed(feedName, userDesc, attributeMap));

        LOGGER.debug("feedName: '{}', userDesc: '{}', optFeedDoc: {}",
                feedName, userDesc, optFeedDoc);

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
                                         final AttributeMap attributeMap) {

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
                    docRef = createFeedAndContent(feedName, userDesc, attributeMap);
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
                                        final AttributeMap attributeMap) {

        final String destinationPath = cachedDestinationPathTemplator.getValue()
                .apply(attributeMap);
        final DocPath docPath = DocPath.fromPathString(destinationPath);

        LOGGER.info("Ensuring path '{}' exists", docPath);
        final ExplorerNode destFolder = explorerService.ensureFolderPath(docPath, PermissionInheritance.DESTINATION);
        final DocRef destFolderRef = destFolder.getDocRef();
        final UserRef userRef;

        if (userDesc != null) {
            LOGGER.info("Ensuing user with userRef: '{}' exists", userDesc);
            final User user = userService.getOrCreateUser(userDesc);
            userRef = user.asRef();
        } else {
            LOGGER.info("No user details, won't ensure Stroom user or add any users to groups.");
            userRef = null;
        }

        final String groupName = cachedGroupTemplator.getValue().apply(attributeMap);
        LOGGER.info("Auto-creating user group '{}'", groupName);
        final User group = userService.getOrCreateUserGroup(groupName);
        addAppPerms(group);
        if (userRef != null) {
            LOGGER.info("Adding userRef {} to group '{}", userRef, groupName);
            userService.addUserToGroup(userRef, group.asRef());
        }

        Optional<User> optAdditionalGroup = Optional.empty();
        final String additionalGroupName = cachedAdditionalGroupTemplator.getValue()
                .apply(attributeMap);
        if (NullSafe.isNonBlankString(additionalGroupName)) {
            LOGGER.info("Auto-creating user group '{}'", additionalGroupName);
            final User additionalGroup = userService.getOrCreateUserGroup(additionalGroupName);
            addAppPerms(additionalGroup);
            if (userRef != null) {
                LOGGER.info("Adding userRef {} to additional group '{}", userRef, additionalGroupName);
                userService.addUserToGroup(userRef, additionalGroup.asRef());
            }
            optAdditionalGroup = Optional.of(additionalGroup);
        }

        LOGGER.info("Auto-creating feed {} in path '{}'", feedName, docPath);
        // Creates the node and the doc
        final DocRef feedDocRef = explorerService.create(
                FeedDoc.TYPE,
                feedName,
                destFolder,
                PermissionInheritance.DESTINATION).getDocRef();

        FeedDoc feedDoc = feedStore.readDocument(feedDocRef);
        // Set up the feed doc using the information in the data feed key
        configureFeed(feedDoc, attributeMap, userRef);
        feedDoc = feedStore.writeDocument(feedDoc);

        LOGGER.info("Granting READ permission on {} and {}", destFolderRef, feedDocRef);
        setUpdateDocPerms(group, destFolderRef, DocumentPermission.VIEW);
        setUpdateDocPerms(group, feedDocRef, DocumentPermission.VIEW);

        optAdditionalGroup.ifPresent(additionalGroup -> {
            LOGGER.info("Granting UPDATE permission on {} and {}", destFolderRef, feedDocRef);
            setUpdateDocPerms(additionalGroup, destFolderRef, DocumentPermission.EDIT);
            setUpdateDocPerms(additionalGroup, feedDocRef, DocumentPermission.EDIT);
        });

        createTemplatedContent(attributeMap, feedDocRef, destFolder);

        LOGGER.debug("feedDoc after configuration: {}", feedDoc);

        return feedDocRef;
    }

    private void configureFeed(final FeedDoc feedDoc,
                               final AttributeMap attributeMap,
                               final UserRef userRef) {
        if (NullSafe.hasEntries(attributeMap)) {
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

            // By this point all the entries in DataFeedKey.streamMetaData have been set in attributeMap
            // so we can just use that.
            consumeAttrVal(attributeMap, StandardHeaderArguments.TYPE, type -> {
                if (NullSafe.set(receiveDataConfig.getMetaTypes()).contains(type)) {
                    feedDoc.setStreamType(type);
                }
            });

            if (userRef != null) {
                feedDoc.setDescription("Auto-created for user '" + userRef.toDisplayString() + "'");
            } else {
                feedDoc.setDescription("Auto-created");
            }
            feedDoc.setStatus(FeedStatus.RECEIVE);

            consumeAttrVal(attributeMap, StandardHeaderArguments.ENCODING, val ->
                    feedDoc.setEncoding(getEncoding(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.CONTEXT_ENCODING, val ->
                    feedDoc.setContextEncoding(getEncoding(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.CLASSIFICATION, feedDoc::setClassification);
            consumeAttrVal(attributeMap, StandardHeaderArguments.FORMAT, val ->
                    feedDoc.setDataFormat(getFormat(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.CONTEXT_FORMAT, val ->
                    feedDoc.setContextFormat(getFormat(val, feedDoc)));
            consumeAttrVal(attributeMap, StandardHeaderArguments.SCHEMA, feedDoc::setSchema);
            consumeAttrVal(attributeMap, StandardHeaderArguments.SCHEMA_VERSION, feedDoc::setSchemaVersion);
        }
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

    private void addAppPerms(final User user) {
        final UserRef userRef = user.asRef();
        appPermissionService.addPermission(userRef, AppPermission.VIEW_DATA_PERMISSION);
        appPermissionService.addPermission(userRef, AppPermission.EXPORT_DATA_PERMISSION);
        appPermissionService.addPermission(userRef, AppPermission.IMPORT_DATA_PERMISSION);
        appPermissionService.addPermission(userRef, AppPermission.STEPPING_PERMISSION);
    }

    private void setUpdateDocPerms(final User user,
                                   final DocRef docRef,
                                   final DocumentPermission perm) {
        documentPermissionService.setPermission(docRef, user.asRef(), perm);
    }

    private Optional<ContentTemplate> getMatchingTemplate(final AttributeMap attributeMap) {

        final ContentTemplates contentTemplates = contentTemplateStore.getOrCreate();
        final List<ContentTemplate> activeTemplates = contentTemplates.getActiveTemplates();
        ContentTemplate matchingTemplate = null;
        if (NullSafe.hasItems(activeTemplates)) {
            for (final ContentTemplate contentTemplate : activeTemplates) {
                final ExpressionOperator expression = contentTemplate.getExpression();
                if (expression == null) {
                    matchingTemplate = contentTemplate;
                    break;
                } else {
                    // Normalise the keys to lower case
                    final Map<String, Object> attributes = attributeMap.asMap(true)
                            .entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    entry1 -> normaliseField(entry1.getKey()),
                                    entry -> NullSafe.get(entry.getValue(), val -> (Object) val)));

                    final boolean isMatch = cachedExpressionMatcher.getValue()
                            .match(attributes, expression);
                    if (isMatch) {
                        matchingTemplate = contentTemplate;
                        break;
                    }
                }
            }
        }
        LOGGER.debug("matchingTemplate: {}", matchingTemplate);
        return Optional.ofNullable(matchingTemplate);
    }

    private static String normaliseField(final String field) {
        return NullSafe.get(
                field,
                String::trim,
                String::toLowerCase);
    }

    private void createTemplatedContent(final AttributeMap attributeMap,
                                        final DocRef feedDocRef,
                                        final ExplorerNode destFolder) {

        getMatchingTemplate(attributeMap)
                .ifPresent(contentTemplate -> {
                    final DocRef pipelineDocRef = Objects.requireNonNull(contentTemplate.getPipeline());
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

                    switch (contentTemplate.getTemplateType()) {

                        case PROCESSOR_FILTER -> createProcessorFilter(
                                attributeMap.get(StandardHeaderArguments.TYPE),
                                contentTemplate.getPipeline(),
                                feedDocRef,
                                contentTemplate);

                        case INHERIT_PIPELINE -> createPipelineFromParent(
                                pipelineDoc,
                                attributeMap.get(StandardHeaderArguments.TYPE),
                                feedDocRef,
                                destFolder,
                                contentTemplate);
                    }
                });
    }

    private void createProcessorFilter(final String streamType,
                                       final DocRef pipelineDocRef,
                                       final DocRef feedDocRef,
                                       final ContentTemplate contentTemplate) {
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

        LOGGER.info("Created processor filter using contentTemplate '{}' for expression: {}, running as {}",
                contentTemplate.getName(), expression, runAsUser);
    }

    private void createPipelineFromParent(final PipelineDoc parentPipelineDoc,
                                          final String streamType,
                                          final DocRef feedDocRef,
                                          final ExplorerNode destFolder,
                                          final ContentTemplate contentTemplate) {

        explorerNodeService.getNode(parentPipelineDoc.asDocRef());
        // Use feed name for the name of the new pipeline
        final String pipeDocName = feedDocRef.getName();
        final ExplorerNode newPipelineNode = explorerService.create(
                PipelineDoc.TYPE,
                pipeDocName,
                destFolder,
                PermissionInheritance.DESTINATION);

        final String newPipelineUuid = newPipelineNode.getUuid();
        final PipelineDoc newPipelineDoc = pipelineService.fetch(newPipelineUuid);
        newPipelineDoc.setParentPipeline(parentPipelineDoc.asDocRef());
        pipelineService.update(newPipelineUuid, newPipelineDoc);

        LOGGER.info("Created pipeline {} with parentPipeline {} using contentTemplate '{}'",
                newPipelineDoc.asDocRef(), parentPipelineDoc.asDocRef(), contentTemplate.getName());

        // Now create the proc filter for the new pipe
        createProcessorFilter(streamType, newPipelineDoc.asDocRef(), feedDocRef, contentTemplate);
    }
}
