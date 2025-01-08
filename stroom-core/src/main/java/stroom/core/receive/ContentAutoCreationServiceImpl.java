package stroom.core.receive;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.shared.DataFormatNames;
import stroom.receive.common.AutoContentCreationConfig;
import stroom.receive.common.DataFeedKey;
import stroom.receive.common.DataFeedKeyService;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.UserAppPermissionService;
import stroom.security.api.UserService;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.DocPath;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.SimpleUserName;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;

public class ContentAutoCreationServiceImpl implements ContentAutoCreationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentAutoCreationServiceImpl.class);
    private static final String LOCK_NAME = "AUTO_CONTENT_CREATION";

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider;
    private final DocumentPermissionService documentPermissionService;
    private final UserAppPermissionService userAppPermissionService;
    private final UserService userService;
    private final DataFeedKeyService dataFeedKeyService;
    private final FeedStore feedStore;
    private final ExplorerService explorerService;
    private final ClusterLockService clusterLockService;
    private final MetaService metaService;

    @Inject
    public ContentAutoCreationServiceImpl(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                                          final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider,
                                          final DocumentPermissionService documentPermissionService,
                                          final UserAppPermissionService userAppPermissionService,
                                          final UserService userService,
                                          final DataFeedKeyService dataFeedKeyService,
                                          final FeedStore feedStore,
                                          final ExplorerService explorerService,
                                          final ClusterLockService clusterLockService,
                                          final MetaService metaService) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.autoContentCreationConfigProvider = autoContentCreationConfigProvider;
        this.documentPermissionService = documentPermissionService;
        this.userAppPermissionService = userAppPermissionService;
        this.userService = userService;
        this.dataFeedKeyService = dataFeedKeyService;
        this.feedStore = feedStore;
        this.explorerService = explorerService;
        this.clusterLockService = clusterLockService;
        this.metaService = metaService;
    }

    @Override
    public Optional<FeedDoc> createFeed(final String feedName,
                                        final String subjectId,
                                        final AttributeMap attributeMap) {
        if (NullSafe.isBlankString(subjectId)) {
            // Can't auto create if we have no identity
            return Optional.empty();
        }
        LOGGER.debug("attributeMap: {}", attributeMap);

        final Optional<FeedDoc> optFeedDoc = dataFeedKeyService.getDataFeedKey(subjectId)
                .flatMap(dataFeedKey ->
                        ensureFeed(feedName, subjectId, attributeMap, dataFeedKey));

        LOGGER.debug("feedName: '{}', subjectId: '{}', optFeedDoc: {}",
                feedName, subjectId, optFeedDoc);

        return optFeedDoc;
    }

    private Optional<FeedDoc> ensureFeed(final String feedName,
                                         final String subjectId,
                                         final AttributeMap attributeMap,
                                         final DataFeedKey dataFeedKey) {

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
                    docRef = createFeed(feedName, subjectId, attributeMap, dataFeedKey);
                } catch (EntityServiceException e) {
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

    private DocRef createFeed(final String feedName,
                              final String subjectId,
                              final AttributeMap attributeMap,
                              final DataFeedKey dataFeedKey) {

        final AutoContentCreationConfig config = autoContentCreationConfigProvider.get();
        final String destinationPath = config.getDestinationPath();
        final DocPath docPath = DocPath.fromPathString(destinationPath)
                .append(dataFeedKey.getAccountName());

        LOGGER.info("Ensuring path '{}' exists", docPath);
        final ExplorerNode destFolder = explorerService.ensureFolderPath(docPath, PermissionInheritance.DESTINATION);
        final DocRef destFolderRef = destFolder.getDocRef();
        final String systemName = dataFeedKey.getAccountName();

        LOGGER.info("Ensuing user with subjectId: '{}' exists", subjectId);
        final User user = userService.getOrCreateUser(new SimpleUserName(
                dataFeedKey.getSubjectId(),
                dataFeedKey.getDisplayName(),
                null));
        final String userUuid = user.getUuid();

        LOGGER.info("Auto-creating user group '{}', and adding userUuid {} to it",
                systemName, userUuid);
        final User systemGroup = userService.getOrCreateUserGroup(systemName);
        addAppPerms(systemGroup);
        userService.addUserToGroup(userUuid, systemGroup.getUuid());

        Optional<User> optAdditionalGroup = Optional.empty();
        if (!NullSafe.isBlankString(config.getAdditionalGroupSuffix())) {
            final String name = systemName + config.getAdditionalGroupSuffix();
            LOGGER.info("Auto-creating user group '{}', and adding userUuid {} to it",
                    name, userUuid);
            final User additionalGroup = userService.getOrCreateUserGroup(name);
            addAppPerms(additionalGroup);
            userService.addUserToGroup(userUuid, additionalGroup.getUuid());
            optAdditionalGroup = Optional.of(additionalGroup);
        }

        LOGGER.info("Auto-creating feed {} in path '{}'", feedName, docPath);
        // Creates the node and the doc
        final DocRef feedDocRef = explorerService.create(
                FeedDoc.DOCUMENT_TYPE,
                feedName,
                destFolder,
                PermissionInheritance.DESTINATION).getDocRef();

        FeedDoc feedDoc = feedStore.readDocument(feedDocRef);
        // Set up the feed doc using the information in the data feed key
        configureFeed(feedDoc, attributeMap, dataFeedKey);
        feedDoc = feedStore.writeDocument(feedDoc);

        LOGGER.info("Granting READ permission on {} and {}", destFolderRef, feedDocRef);
        addUpdateDocPerms(systemGroup, destFolderRef, DocumentPermissionNames.READ);
        addUpdateDocPerms(systemGroup, feedDocRef, DocumentPermissionNames.READ);

        optAdditionalGroup.ifPresent(additionalGroup -> {
            LOGGER.info("Granting UPDATE permission on {} and {}", destFolderRef, feedDocRef);
            addUpdateDocPerms(additionalGroup, destFolderRef, DocumentPermissionNames.UPDATE);
            addUpdateDocPerms(additionalGroup, feedDocRef, DocumentPermissionNames.UPDATE);
        });

        LOGGER.debug("feedDoc after configuration: {}", feedDoc);

        return feedDocRef;
    }

    private void configureFeed(final FeedDoc feedDoc,
                               final AttributeMap attributeMap,
                               final DataFeedKey dataFeedKey) {
        if (NullSafe.hasEntries(attributeMap)) {
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

            // By this point all the entries in DataFeedKey.streamMetaData have been set in attributeMap
            // so we can just use that.
            consumeAttrVal(attributeMap, StandardHeaderArguments.TYPE, type -> {
                if (NullSafe.set(receiveDataConfig.getMetaTypes()).contains(type)) {
                    feedDoc.setStreamType(type);
                }
            });

            feedDoc.setDescription("Auto-created for system '" + dataFeedKey.getAccountName() + "'");
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
        userAppPermissionService.addPermission(user.getUuid(), PermissionNames.VIEW_DATA_PERMISSION);
        userAppPermissionService.addPermission(user.getUuid(), PermissionNames.EXPORT_DATA_PERMISSION);
        userAppPermissionService.addPermission(user.getUuid(), PermissionNames.IMPORT_DATA_PERMISSION);
        userAppPermissionService.addPermission(user.getUuid(), PermissionNames.STEPPING_PERMISSION);
    }

    private void addUpdateDocPerms(final User user, final DocRef docRef, final String perm) {
        documentPermissionService.addPermission(docRef.getUuid(), user.getUuid(), perm);
    }
}
