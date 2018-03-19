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

package stroom.entity;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.PermissionException;
import stroom.entity.shared.ProvidesNamePattern;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.explorer.shared.ExplorerConstants;
import stroom.importexport.ImportExportHelper;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.persist.EntityManagerSupport;
import stroom.util.config.StroomProperties;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.persistence.Transient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

// @Transactional
public abstract class DocumentEntityServiceImpl<E extends DocumentEntity, C extends FindDocumentEntityCriteria> implements DocumentEntityService<E>, BaseEntityService<E>, FindService<E, C>, ProvidesNamePattern {
    public static final String FOLDER = ExplorerConstants.FOLDER;
    private static final String NAME_PATTERN_PROPERTY = "stroom.namePattern";
    private static final String NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    public static final String ID = "@ID@";
    public static final String TYPE = "@TYPE@";
    public static final String NAME = "@NAME@";

    private final StroomEntityManager entityManager;
    private final EntityManagerSupport entityManagerSupport;
    private final SecurityContext securityContext;
    private final EntityServiceHelper<E> entityServiceHelper;
    private final FindServiceHelper<E, C> findServiceHelper;
    private final ImportExportHelper importExportHelper;

    private final QueryAppender<E, ?> queryAppender;
    private String entityType;
    private FieldMap sqlFieldMap;

    protected DocumentEntityServiceImpl(final StroomEntityManager entityManager,
                                        final EntityManagerSupport entityManagerSupport,
                                        final ImportExportHelper importExportHelper,
                                        final SecurityContext securityContext) {
        this.entityManager = entityManager;
        this.entityManagerSupport = entityManagerSupport;
        this.importExportHelper = importExportHelper;
        this.securityContext = securityContext;
        this.queryAppender = createQueryAppender(entityManager);
        this.entityServiceHelper = new EntityServiceHelper<>(entityManager, getEntityClass());
        this.findServiceHelper = new FindServiceHelper<>(entityManager, getEntityClass(), queryAppender);
    }

    protected StroomEntityManager getEntityManager() {
        return entityManager;
    }

    protected EntityServiceHelper<E> getEntityServiceHelper() {
        return entityServiceHelper;
    }

    protected QueryAppender<E, ?> getQueryAppender() {
        return queryAppender;
    }

    @Override
    public E create(final String name) throws RuntimeException {
        return create(name, null);
    }

    private E create(final String name, final String parentFolderUUID) throws RuntimeException {
        E entity;

        try {
            // Check create permissions of the parent folder.
            checkCreatePermission(parentFolderUUID);

            // Create a new entity instance.
            try {
                entity = getEntityClass().newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new EntityServiceException(e.getMessage());
            }

            entity.setUuid(UUID.randomUUID().toString());

            // Validate the entity name.
            NameValidationUtil.validate(getNamePattern(), name);
            entity.setName(name);

            entity = entityServiceHelper.save(entity, queryAppender);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        return entity;
    }

    // @Transactional
    @Override
    public E load(final E entity) throws RuntimeException {
        return load(entity, Collections.emptySet());
    }

    // @Transactional
    @Override
    public E load(final E entity, final Set<String> fetchSet) throws RuntimeException {
        if (entity == null) {
            return null;
        }
        return loadById(entity.getId(), fetchSet, queryAppender);
    }

    // @Transactional
    @Override
    public E loadById(final long id) throws RuntimeException {
        return loadById(id, Collections.emptySet(), queryAppender);
    }

    // @Transactional
    @Override
    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
        return loadById(id, fetchSet, queryAppender);
    }

    @SuppressWarnings("unchecked")
    private E loadById(final long id, final Set<String> fetchSet, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        E entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");

        if (queryAppender != null) {
            queryAppender.appendBasicJoin(sql, "e", fetchSet);
        }

        sql.append(" WHERE e.id = ");
        sql.arg(id);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql, null, true);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null) {
            try {
                if (queryAppender != null) {
                    queryAppender.postLoad(entity);
                }
                checkReadPermission(DocRefUtil.create(entity));
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        return entity;
    }

    // @Transactional
    @Override
    public final E loadByUuid(final String uuid) throws RuntimeException {
        return loadByUuid(uuid, null);
    }

    @SuppressWarnings("unchecked")
    // @Transactional
    @Override
    public final E loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        return loadByUuid(uuid, fetchSet, queryAppender);
    }

    protected E loadByUuid(final String uuid, final Set<String> fetchSet, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        E entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        if (queryAppender != null) {
            queryAppender.appendBasicJoin(sql, "e", fetchSet);
        }
        sql.append(" WHERE e.uuid = ");
        sql.arg(uuid);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql, null, true);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null) {
            try {
                if (queryAppender != null) {
                    queryAppender.postLoad(entity);
                }
                checkReadPermission(DocRefUtil.create(entity));
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        return entity;
    }

    // TODO : Remove this method when the explorer service is broken out as a separate micro service.
    @SuppressWarnings("unchecked")
    // @Transactional
    protected E loadByUuidInsecure(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        E entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        queryAppender.appendBasicJoin(sql, "e", fetchSet);
        sql.append(" WHERE e.uuid = ");
        sql.arg(uuid);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql, null, true);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null) {
            try {
                queryAppender.postLoad(entity);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        return entity;
    }

    @Override
    public E save(final E entity) throws RuntimeException {
        return save(entity, queryAppender);
    }

    protected E save(final E entity, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        try {
            if (!entity.isPersistent()) {
                throw new EntityServiceException("You cannot update an entity that has not been created");
            }

            checkUpdatePermission(entity);

            if (entity.getUuid() == null) {
                entity.setUuid(UUID.randomUUID().toString());
            }
            return entityServiceHelper.save(entity, queryAppender);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean delete(final E entity) throws RuntimeException {
        Boolean success;
        try {
            checkDeletePermission(DocRefUtil.create(entity));
            success = entityServiceHelper.delete(entity);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        return success;
    }

    /**
     * This function is used when doing batch document copy operations.
     * <p>
     * It can be used by any entities which serialize themselves into a single string field.
     * If that string field contains all the UUID's of dependant entities, then it is simple a case of
     * string replacing the original UUID's with the UUID's of the copies being made.
     *
     * @param copyDocRef                The Doc Ref of the new copy
     * @param otherCopiesByOriginalUuid The map of copies of other documents, by their original UUID
     * @param dataSupplier              The getter
     * @param dataReceiver              The setter
     */
    protected DocRef makeCopyUuidReplacements(final DocRef copyDocRef,
                                              final Map<String, String> otherCopiesByOriginalUuid,
                                              final Function<E, String> dataSupplier,
                                              final BiConsumer<E, String> dataReceiver) {

        final E copiedEntity = loadByUuid(copyDocRef.getUuid(), Collections.emptySet(), null);

        // Rewrite UUID's of dependant documents
        String modifiedData = dataSupplier.apply(copiedEntity);
        for (final Map.Entry<String, String> copyByOriginal : otherCopiesByOriginalUuid.entrySet()) {
            modifiedData = modifiedData.replaceAll(copyByOriginal.getKey(), copyByOriginal.getValue());
        }

        dataReceiver.accept(copiedEntity, modifiedData);

        save(copiedEntity, null);

        return copyDocRef;
    }

    private E copy(final E document,
                   final String copyUuid,
                   final String parentFolderUUID) {
        try {
            // Ensure we are working with effectively a 'new document'
            entityManager.detach(document);

            // Check create permissions of the parent folder.
            checkCreatePermission(parentFolderUUID);

            // This is going to be a copy so clear the persistence so save will create a new DB entry.
            document.clearPersistence();

            document.setUuid(copyUuid);

            return entityServiceHelper.save(document, queryAppender);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private E move(final E document, final String parentFolderUUID) {
        try {
            // Check create permissions of the parent folder.
            checkCreatePermission(parentFolderUUID);

            return entityServiceHelper.save(document, queryAppender);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private E rename(final E document, final String name) {
        try {
            // Validate the entity name.
            NameValidationUtil.validate(getNamePattern(), name);
            document.setName(name);

            return entityServiceHelper.save(document, queryAppender);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }


    // @Transactional
    @Override
    public BaseResultList<E> find(final C criteria) throws RuntimeException {
        // Make sure the required permission is a valid one.
        String permission = criteria.getRequiredPermission();
        if (permission == null) {
            permission = DocumentPermissionNames.READ;
        } else if (!DocumentPermissionNames.isValidPermission(permission)) {
            throw new IllegalArgumentException("Unknown permission " + permission);
        }

        // Find documents using the supplied criteria.
        // We do not want to limit the results by offset or length at this point as we will filter out results later based on user permissions.
        // We will only limit the returned number of results once we have applied permission filtering.
        final PageRequest pageRequest = criteria.getPageRequest();
        criteria.setPageRequest(null);
        final List<E> list = findServiceHelper.find(criteria, getSqlFieldMap());
        criteria.setPageRequest(pageRequest);

        // Filter the results to only include documents that the current user has permission to see.
        final List<E> filtered = filterResults(list, permission);

        // Create a result list limited by the page request.
        return BaseResultList.createPageLimitedList(filtered, pageRequest);
    }

    private List<E> filterResults(final List<E> list, final String permission) {
        return list.stream().filter(e -> securityContext.hasDocumentPermission(e.getType(), e.getUuid(), permission)).collect(Collectors.toList());
    }

    protected E internalSave(final E entity) {
        return entityManager.saveEntity(entity);
    }

    @Transient
    @Override
    public String getNamePattern() {
        return StroomProperties.getProperty(NAME_PATTERN_PROPERTY, NAME_PATTERN_VALUE);
    }

    private String getDocReference(BaseEntity entity) {
        if (entity == null) {
            return "";
        }
        return "(" + DocRefUtil.create(entity).toString() + ")";
    }

    public abstract Class<E> getEntityClass();

    public String getEntityType() {
        if (entityType == null) {
            try {
                entityType = getEntityClass().newInstance().getType();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
        return entityType;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    public DocRef importDocument(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        E entity = null;

        try {
            // See if a document already exists with this uuid.
            entity = loadByUuid(docRef.getUuid(), Collections.singleton("all"));
            if (entity == null) {
                entity = getEntityClass().newInstance();
            }

            importExportHelper.performImport(entity, dataMap, importState, importMode);

            // Save directly so there is no marshalling of objects that would destroy imported data.
            if (importState.ok(importMode)) {
                entity = internalSave(entity);
            }

        } catch (final Exception e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
        }

        return DocRefUtil.create(entity);
    }

    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT)) {
            return entityManagerSupport.executeResult(em -> {
                final E entity = entityServiceHelper.loadByUuid(docRef.getUuid(), Collections.emptySet(), queryAppender);
                if (entity != null) {
                    return importExportHelper.performExport(entity, omitAuditFields, messageList);
                }

                return Collections.emptyMap();
            });
        }

        return Collections.emptyMap();
    }

    public Set<DocRef> listDocuments() {
        final List<E> list = find(createCriteria());
        return list.stream().map(DocRefUtil::create).collect(Collectors.toSet());
    }

    public Map<DocRef, Set<DocRef>> getDependencies() {
        final List<E> list = find(createCriteria());
        return list.stream().map(DocRefUtil::create).collect(Collectors.toMap(Function.identity(), d -> Collections.emptySet()));
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    public final DocRef createDocument(final String name, final String parentFolderUUID) {
        return DocRefUtil.create(create(name, parentFolderUUID));
    }

    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid,
                               final String parentFolderUUID) {
        final E entity = loadByUuid(originalUuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        final E copy = copy(entity, copyUuid, parentFolderUUID);
        return DocRefUtil.create(copy);
    }

    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        return DocRefUtil.create(move(entity, parentFolderUUID));
    }

    public DocRef renameDocument(final String uuid, final String name) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        return DocRefUtil.create(rename(entity, name));
    }

    public void deleteDocument(final String uuid) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        delete(entity);
    }

    public DocRefInfo info(final String uuid) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        return new DocRefInfo.Builder()
                .docRef(new DocRef.Builder()
                        .type(entity.getType())
                        .uuid(entity.getUuid())
                        .name(entity.getName())
                        .build())
                .otherInfo("DB ID: " + entity.getId())
                .createUser(entity.getCreateUser())
                .createTime(entity.getCreateTime())
                .updateUser(entity.getUpdateUser())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    // @Transactional
    @Override
    public E readDocument(final DocRef docRef) {
        return loadByUuid(docRef.getUuid());
    }

    @SuppressWarnings("unchecked")
    @Override
    public E writeDocument(final E document) {
        return save(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    protected QueryAppender<E, ?> createQueryAppender(final StroomEntityManager entityManager) {
        return new QueryAppender<>(entityManager);
    }

    private void checkCreatePermission(final String folderUUID) {
        // Only allow administrators to create documents with no folder.
        if (folderUUID == null) {
            if (!securityContext.isAdmin()) {
                throw new PermissionException(securityContext.getUserId(), "Only administrators can create root level entries");
            }
        } else {
            if (!securityContext.hasDocumentPermission(FOLDER, folderUUID, DocumentPermissionNames.getDocumentCreatePermission(getEntityType()))) {
                throw new PermissionException(securityContext.getUserId(), "You do not have permission to create (" + getEntityType() + ") in folder " + folderUUID);
            }
        }
    }

    protected void checkUpdatePermission(final E entity) {
        if (!entity.isPersistent()) {
            throw new PermissionException(securityContext.getUserId(), "You cannot update an entity that has not been created " + getDocReference(entity));
        }
        checkUpdatePermission(DocRefUtil.create(entity));
    }

    private void checkUpdatePermission(final DocRef docRef) {
        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.UPDATE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to update (" + docRef + ")");
        }
    }

    protected final void checkReadPermission(final DocRef docRef) {
        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to read (" + docRef + ")");
        }
    }

    protected void checkDeletePermission(final DocRef docRef) {
        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.DELETE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to delete (" + docRef + ")");
        }
    }

    protected FieldMap createFieldMap() {
        return new FieldMap()
                .add(BaseCriteria.FIELD_ID, BaseEntity.ID, "id")
                .add(FindNamedEntityCriteria.FIELD_NAME, NamedEntity.NAME, "name");
    }

    private FieldMap getSqlFieldMap() {
        if (sqlFieldMap == null) {
            sqlFieldMap = createFieldMap();
        }
        return sqlFieldMap;
    }
}