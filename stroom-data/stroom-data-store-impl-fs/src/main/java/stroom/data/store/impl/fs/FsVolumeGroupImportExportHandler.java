package stroom.data.store.impl.fs;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasDocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.shared.DocRefUtil;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.AuditUtil;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Message;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FsVolumeGroupImportExportHandler
        implements ImportExportActionHandler, NonExplorerDocRefProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeGroupImportExportHandler.class);

    private static final String META = "meta";

    private final FsVolumeGroupService fsVolumeGroupService;
    private final ImportExportDocumentEventLog importExportDocumentEventLog;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    private final DocumentSerialiser2<FsVolumeGroup> serialiser;
    private final SecurityContext securityContext;
    private final EntityEventBus entityEventBus;

    @Inject
    public FsVolumeGroupImportExportHandler(final FsVolumeGroupService fsVolumeGroupService,
                                            final ImportExportDocumentEventLog importExportDocumentEventLog,
                                            final Provider<DocRefInfoService> docRefInfoServiceProvider,
                                            final Serialiser2Factory serialiser2Factory,
                                            final SecurityContext securityContext,
                                            final EntityEventBus entityEventBus) {
        this.fsVolumeGroupService = fsVolumeGroupService;
        this.importExportDocumentEventLog = importExportDocumentEventLog;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.serialiser = serialiser2Factory.createSerialiser(FsVolumeGroup.class);
        this.securityContext = securityContext;
        this.entityEventBus = entityEventBus;
    }

    @Override
    public DocRef importDocument(DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        if (dataMap != null) {
            Objects.requireNonNull(docRef);
            final String uuid = docRef.getUuid();

            try {
                final FsVolumeGroup existingVolGroup = fsVolumeGroupService.get(docRef);

                if (ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode())) {
                    if (existingVolGroup == null) {
                        importState.setState(State.NEW);
                    } else {
                        docRef = docRef.copy().name(existingVolGroup.getName()).build();

                        if (!securityContext.hasDocumentPermission(uuid, DocumentPermissionNames.UPDATE)) {
                            throw new PermissionException(
                                    securityContext.getUserIdentityForAudit(),
                                    "You are not authorised to update " + toDocRefDisplayString(docRef));
                        }

                        final List<String> updatedFields = importState.getUpdatedFieldList();
                        checkForUpdatedFields(
                                existingVolGroup,
                                dataMap,
                                new AuditFieldFilter<>(),
                                updatedFields);
                        if (updatedFields.isEmpty()) {
                            importState.setState(State.EQUAL);
                        }
                    }
                } else if (ImportSettings.ok(importSettings, importState)) {
                    if (existingVolGroup != null) {
                        docRef = docRef.copy().name(existingVolGroup.getName()).build();
                        if (!securityContext.hasDocumentPermission(uuid, DocumentPermissionNames.UPDATE)) {
                            throw new PermissionException(
                                    securityContext.getUserIdentityForAudit(),
                                    "You are not authorised to update " + toDocRefDisplayString(docRef));
                        }
                    }
                    importDocument(docRef, existingVolGroup, uuid, dataMap);
                }
            } catch (final RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
            }
        }
        return docRef;
    }

    private void importDocument(final DocRef docRef,
                                final FsVolumeGroup existingVolGroup,
                                final String uuid,
                                final Map<String, byte[]> dataMap) {
        try {
            // Turn the data map into a document.
            final FsVolumeGroup newDocument = serialiser.read(dataMap);
            // Copy create time and user from the existing document.
            if (existingVolGroup != null) {
                newDocument.setName(existingVolGroup.getName());
                newDocument.setCreateTimeMs(existingVolGroup.getCreateTimeMs());
                newDocument.setCreateUser(existingVolGroup.getCreateUser());
            }
            // Stamp audit data on the imported document.
            AuditUtil.stamp(securityContext, newDocument);

            fsVolumeGroupService.update(newDocument);

            // Fire an entity event to alert other services of the change.
            if (existingVolGroup != null) {
                EntityEvent.fire(entityEventBus, docRef, EntityAction.UPDATE);
            } else {
                EntityEvent.fire(entityEventBus, docRef, EntityAction.CREATE);
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        Function<FsVolumeGroup, FsVolumeGroup> filter = omitAuditFields
                ? new AuditFieldFilter<>()
                : Function.identity();
        return exportDocument(docRef, messageList, filter);
    }

    private Map<String, byte[]> exportDocument(final DocRef docRef,
                                               final List<Message> messageList,
                                               final Function<FsVolumeGroup, FsVolumeGroup> filter) {
        Map<String, byte[]> data = Collections.emptyMap();

        try {
            // Check that the user has permission to read this item.
            if (!securityContext.hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.READ)) {
                throw new PermissionException(
                        securityContext.getUserIdentityForAudit(),
                        "You are not authorised to read " + toDocRefDisplayString(docRef));
            } else {
                FsVolumeGroup document = fsVolumeGroupService.get(docRef);
                if (document == null) {
                    throw new DocumentNotFoundException(docRef);
                }
                document = filter.apply(document);
                data = serialiser.write(document);
            }
        } catch (final IOException e) {
            messageList.add(new Message(Severity.ERROR, e.getMessage()));
        }

        return data;
    }

    @Override
    public Set<DocRef> listDocuments() {
        return NullSafe.stream(fsVolumeGroupService.getAll())
                .map(HasDocRef::asDocRef)
                .collect(Collectors.toSet());
    }

    @Override
    public String getType() {
        return FsVolumeGroup.DOCUMENT_TYPE;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        // Vol groups have no associations
        return Collections.emptySet();
    }

    @Override
    public DocRef findNearestExplorerDocRef(final DocRef docref) {
        return null;
    }

    @Override
    public DocRefInfo info(final String uuid) {
        final DocRef docRef = FsVolumeGroup.buildDocRef()
                .uuid(uuid)
                .build();
        return Optional.ofNullable(fsVolumeGroupService.get(docRef))
                .map(fsVolumeGroup ->
                        DocRefInfo.builder()
                                .docRef(fsVolumeGroup.asDocRef())
                                .createTime(fsVolumeGroup.getCreateTimeMs())
                                .createUser(fsVolumeGroup.getCreateUser())
                                .updateTime(fsVolumeGroup.getUpdateTimeMs())
                                .updateUser(fsVolumeGroup.getUpdateUser())
                                .build())
                .orElseThrow(() -> new IllegalArgumentException(LogUtil.message(
                        "FS Volume Group with UUID {} not found", uuid)));
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        // No deps
        return Collections.emptyMap();
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        // No deps
        return Collections.emptySet();
    }

    @Override
    public void remapDependencies(final DocRef docRef, final Map<DocRef, DocRef> remappings) {
        // No deps
    }

    private String toDocRefDisplayString(final DocRef docRef) {
        if (docRef == null || !NullSafe.isBlankString(docRef.getName())) {
            return "";
        } else {
            try {
                return DocRefUtil.createTypedDocRefString(docRefInfoServiceProvider.get()
                        .decorate(docRef));
            } catch (Exception e) {
                // This method is for use in decorating the docref for exception messages
                // so swallow any errors.
                LOGGER.debug("Error decorating docRef {}: {}",
                        docRef, LogUtil.exceptionMessage(e), e);
                try {
                    return DocRefUtil.createTypedDocRefString(docRef);
                } catch (Exception ex) {
                    LOGGER.debug("Error displaying docRef {}: {}",
                            docRef, LogUtil.exceptionMessage(e), e);
                    return docRef.toString();
                }
            }
        }
    }

    private void checkForUpdatedFields(final FsVolumeGroup existingDoc,
                                       final Map<String, byte[]> dataMap,
                                       final Function<FsVolumeGroup, FsVolumeGroup> filter,
                                       final List<String> updatedFieldList) {
        try {
            final FsVolumeGroup newDoc = serialiser.read(dataMap);
            final FsVolumeGroup existingDocument = filter.apply(existingDoc);
            final FsVolumeGroup newDocument = filter.apply(newDoc);

            try {
                final Method[] methods = existingDocument.getClass().getMethods();
                for (final Method method : methods) {
                    String field = method.getName();
                    if (field.length() > 4 && field.startsWith("get") && method.getParameterTypes().length == 0) {
                        final Object existingObject = method.invoke(existingDocument);
                        final Object newObject = method.invoke(newDocument);
                        if (!Objects.equals(existingObject, newObject)) {
                            field = field.substring(3);
                            field = field.substring(0, 1).toLowerCase() + field.substring(1);

                            updatedFieldList.add(field);
                        }
                    }
                }
            } catch (final InvocationTargetException | IllegalAccessException e) {
                LOGGER.error(e.getMessage(), e);
            }

        } catch (final RuntimeException | IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
