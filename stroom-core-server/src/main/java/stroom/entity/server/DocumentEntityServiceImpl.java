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

package stroom.entity.server;

import org.springframework.transaction.annotation.Transactional;
import stroom.dashboard.server.logging.DocumentEventLog;
import stroom.entity.server.util.FieldMap;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.ImportState.State;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.PermissionException;
import stroom.entity.shared.ProvidesNamePattern;
import stroom.importexport.server.Config;
import stroom.importexport.server.ImportExportHelper;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.config.StroomProperties;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.persistence.Transient;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@AutoMarshal
public abstract class DocumentEntityServiceImpl<E extends DocumentEntity, C extends FindDocumentEntityCriteria> implements DocumentEntityService<E>, BaseEntityService<E>, FindService<E, C>, ProvidesNamePattern {
    private static final String NAME_PATTERN_PROPERTY = "stroom.namePattern";
    private static final String NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    public static final String ID = "@ID@";
    public static final String TYPE = "@TYPE@";
    public static final String NAME = "@NAME@";

    //    protected static final String[] STANDARD_PERMISSIONS = new String[]{DocumentPermissionNames.USE,
//            DocumentPermissionNames.READ, DocumentPermissionNames.UPDATE, DocumentPermissionNames.DELETE, DocumentPermissionNames.OWNER};
    private final StroomEntityManager entityManager;
    private final SecurityContext securityContext;
    private final DocumentEventLog documentEventLog;
    private final EntityServiceHelper<E> entityServiceHelper;
    private final FindServiceHelper<E, C> findServiceHelper;
    private final ImportExportHelper importExportHelper;

    private final QueryAppender<E, ?> queryAppender;
    private String entityType;
    private FieldMap sqlFieldMap;

    protected DocumentEntityServiceImpl(final StroomEntityManager entityManager,
                                        final ImportExportHelper importExportHelper,
                                        final SecurityContext securityContext, final DocumentEventLog documentEventLog) {
        this.entityManager = entityManager;
        this.importExportHelper = importExportHelper;
        this.securityContext = securityContext;
        this.documentEventLog = documentEventLog;
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
            NameValidationUtil.validate(this, name);
            entity.setName(name);

            entity = entityServiceHelper.save(entity, queryAppender);

            documentEventLog.create(entity, null);

        } catch (final RuntimeException e) {
            documentEventLog.create(getEntityType(), name, e);
            throw e;
        } catch (final Exception e) {
            documentEventLog.create(getEntityType(), name, e);
            throw new RuntimeException(e);
        }

        return entity;
    }

    @Transactional(readOnly = true)
    @Override
    public E load(final E entity) throws RuntimeException {
        return load(entity, Collections.emptySet());
    }

    @Transactional(readOnly = true)
    @Override
    public E load(final E entity, final Set<String> fetchSet) throws RuntimeException {
        if (entity == null) {
            return null;
        }
        return loadById(entity.getId(), fetchSet, queryAppender);
    }

    @Transactional(readOnly = true)
    @Override
    public E loadById(final long id) throws RuntimeException {
        return loadById(id, Collections.emptySet(), queryAppender);
    }

    @Transactional(readOnly = true)
    @Override
    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
        return loadById(id, fetchSet, queryAppender);
    }

    @SuppressWarnings("unchecked")
    protected E loadById(final long id, final Set<String> fetchSet, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
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
                documentEventLog.view(entity);
            } catch (final RuntimeException e) {
                documentEventLog.view(entity, e);
                throw e;
            } catch (final Exception e) {
                documentEventLog.view(entity, e);
                throw new RuntimeException(e);
            }
        }

        return entity;
    }

//    // TODO : Remove this method when the explorer service is broken out as a separate micro service.
//    @SuppressWarnings("unchecked")
//    @Transactional(readOnly = true)
//    @Override
//    public E loadByIdInsecure(final long id, final Set<String> fetchSet) throws RuntimeException {
//        E entity = null;
//
//        final HqlBuilder sql = new HqlBuilder();
//        sql.append("SELECT e");
//        sql.append(" FROM ");
//        sql.append(getEntityClass().getName());
//        sql.append(" AS e");
//
//        queryAppender.appendBasicJoin(sql, "e", fetchSet);
//
//        sql.append(" WHERE e.id = ");
//        sql.arg(id);
//
//        final List<E> resultList = getEntityManager().executeQueryResultList(sql, null, true);
//        if (resultList != null && resultList.size() > 0) {
//            entity = resultList.get(0);
//        }
//
//        if (entity != null) {
//            queryAppender.postLoad(entity);
//        }
//
//        return entity;
//    }

    @Transactional(readOnly = true)
    @Override
    public final E loadByUuid(final String uuid) throws RuntimeException {
        return loadByUuid(uuid, null);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    @Override
    public final E loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
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
                checkReadPermission(DocRefUtil.create(entity));
                documentEventLog.view(entity);
            } catch (final RuntimeException e) {
                documentEventLog.view(entity, e);
                throw e;
            } catch (final Exception e) {
                documentEventLog.view(entity, e);
                throw new RuntimeException(e);
            }
        }

        return entity;
    }

    // TODO : Remove this method when the explorer service is broken out as a separate micro service.
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
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
//                checkReadPermission(DocRefUtil.create(entity));
                documentEventLog.view(entity);
            } catch (final RuntimeException e) {
                documentEventLog.view(entity, e);
                throw e;
            } catch (final Exception e) {
                documentEventLog.view(entity, e);
                throw new RuntimeException(e);
            }
        }

        return entity;
    }
//
//    @Override
//    public final E loadByName(final DocRef folder, final String name) throws RuntimeException {
//        return loadByName(folder, name, null);
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public final E loadByName(final DocRef folder, final String name, final Set<String> fetchSet) throws RuntimeException {
//        final SQLBuilder sql = new SQLBuilder();
//        sql.append("SELECT e FROM ");
//        sql.append(getEntityClass().getName());
//        sql.append(" AS e");
//        queryAppender.appendBasicJoin(sql, "e", fetchSet);
//        sql.append(" WHERE e.name = ");
//        sql.arg(name);
//
//        final Class<?> clazz = getEntityClass();
//        if (DocumentEntity.class.isAssignableFrom(clazz)) {
//            // For some reason this doesn't work on folders themselves?
//            if (!getEntityClass().equals(Folder.class)) {
//                if (folder == null) {
//                    sql.append(" AND e.folder IS NULL");
//                } else {
//                    sql.append(" AND e.folder.uuid = ");
//                    sql.arg(folder.getUuid());
//                }
//            }
//        }
//
//        final BaseResultList<E> list = BaseResultList.createUnboundedList(entityManager.executeQueryResultList(sql));
//
//        // FIXME: Fix once folders have been removed from entities. For now filter by parent group id manually
//        E entity = null;
//        if (getEntityClass().equals(Folder.class)) {
//            for (final E e : list) {
//                if (folder == null) {
//                    if (e.getFolder() == null) {
//                        entity = e;
//                        break;
//                    }
//                } else {
//                    if (EqualsUtil.isEquals(folder.getUuid(), e.getFolder().getUuid())) {
//                        entity = e;
//                        break;
//                    }
//                }
//            }
//        } else {
//            entity = list.getFirst();
//        }
//
//        if (entity != null) {
//            queryAppender.postLoad(entity);
//            checkReadPermission(entity);
//        }
//
//        return entity;
//    }

    @Override
    public E save(final E entity) throws RuntimeException {
        return save(entity, queryAppender);
    }

    protected E save(final E entity, final QueryAppender<E, ?> queryAppender) throws RuntimeException {
        E before = entity;
        E after = before;

        try {
            if (!entity.isPersistent()) {
                throw new EntityServiceException("You cannot update an entity that has not been created");
            }

            checkUpdatePermission(entity);

            if (entity.getUuid() == null) {
                entity.setUuid(UUID.randomUUID().toString());
            }
            after = entityServiceHelper.save(entity, queryAppender);
            documentEventLog.update(before, after, null);
        } catch (final RuntimeException e) {
            documentEventLog.update(before, after, null);
            throw e;
        } catch (final Exception e) {
            documentEventLog.update(before, after, null);
            throw new RuntimeException(e);
        }

        return after;
    }

//    @Override
//    public Boolean delete(final DocRef item) {
//        final E entity = loadByUuid(item.getUuid());
//        if (entity != null) {
//            return delete(entity);
//        }
//
//        // If we couldn't find the entity then it must have been deleted already so return true.
//        return true;
//    }

    @Override
    public Boolean delete(final E entity) throws RuntimeException {
        Boolean success;
        try {
            checkDeletePermission(DocRefUtil.create(entity));
            success = entityServiceHelper.delete(entity);
            documentEventLog.delete(entity, null);
        } catch (final RuntimeException e) {
            documentEventLog.delete(entity, e);
            throw e;
        } catch (final Exception e) {
            documentEventLog.delete(entity, e);
            throw new RuntimeException(e);
        }

        return success;
    }

    private E copy(final E document, final String name, final String parentFolderUUID) {
        E before = document;
        E after = before;

        try {
            // Check create permissions of the parent folder.
            checkCreatePermission(parentFolderUUID);

            // This is going to be a copy so clear the persistence so save will create a new DB entry.
            after.clearPersistence();

            after.setUuid(UUID.randomUUID().toString());

            // Validate the entity name.
            NameValidationUtil.validate(this, name);
            after.setName(name);

            after = entityServiceHelper.save(after, queryAppender);

            documentEventLog.copy(before, after, null);

        } catch (final RuntimeException e) {
            documentEventLog.copy(before, after, e);
            throw e;
        } catch (final Exception e) {
            documentEventLog.copy(before, after, e);
            throw new RuntimeException(e);
        }

        return after;
    }

    private E move(final E document, final String parentFolderUUID) {
        E before = document;
        E after = before;

        try {
            // Check create permissions of the parent folder.
            checkCreatePermission(parentFolderUUID);

            after = entityServiceHelper.save(after, queryAppender);

            documentEventLog.move(before, after, null);

        } catch (final RuntimeException e) {
            documentEventLog.move(before, after, e);
            throw e;
        } catch (final Exception e) {
            documentEventLog.move(before, after, e);
            throw new RuntimeException(e);
        }

        return after;
    }

    private E rename(final E document, final String name) {
        E before = document;
        E after = before;

        try {
            // Validate the entity name.
            NameValidationUtil.validate(this, name);
            after.setName(name);

            after = entityServiceHelper.save(after, queryAppender);

            documentEventLog.rename(before, after, null);

        } catch (final RuntimeException e) {
            documentEventLog.rename(before, after, e);
            throw e;
        } catch (final Exception e) {
            documentEventLog.rename(before, after, e);
            throw new RuntimeException(e);
        }

        return after;
    }


    //    @Override
//    @Transactional(readOnly = true)
//    @SuppressWarnings("unchecked")
//    public List<E> findByFolder(final DocRef folder, final Set<String> fetchSet) throws RuntimeException {
//        final SQLBuilder sql = new SQLBuilder();
//        sql.append("SELECT e FROM ");
//        sql.append(getEntityClass().getName());
//        sql.append(" AS e");
//
//        queryAppender.appendBasicJoin(sql, "e", fetchSet);
//
//        sql.append(" WHERE 1=1");
//
//        if (folder != null) {
//            sql.append(" AND e.folder.uuid = ");
//            sql.arg(folder.getUuid());
//        }
//
//        final List<E> list = entityManager.executeQueryResultList(sql);
//        return filterResults(list, DocumentPermissionNames.READ);
//    }
//
    @Transactional(readOnly = true)
    @Override
    public BaseResultList<E> find(final C criteria) throws RuntimeException {
        // Make sure the required permission is a valid one.
        String permission = criteria.getRequiredPermission();
        if (permission == null) {
            permission = DocumentPermissionNames.READ;
        } else if (!DocumentPermissionNames.isValidPermission(permission)) {
            throw new IllegalArgumentException("Unknown permission " + permission);
        }

        BaseResultList<E> result = null;

        // Find documents using the supplied criteria.
        // We do not want to limit the results by offset or length at this point as we will filter out results later based on user permissions.
        // We will only limit the returned number of results once we have applied permission filtering.
        final PageRequest pageRequest = criteria.getPageRequest();
        criteria.setPageRequest(null);
        final List<E> list = findServiceHelper.find(criteria, getSqlFieldMap());
        criteria.setPageRequest(pageRequest);

        // Filter the results to only include documents that the current user has permission to see.
        final List<E> filtered = filterResults(list, permission);

        if (pageRequest != null) {
            int offset = 0;
            int length = filtered.size();

            if (pageRequest.getOffset() != null) {
                offset = pageRequest.getOffset().intValue();
            }

            if (pageRequest.getLength() != null) {
                length = Math.min(length, pageRequest.getLength());
            }

            // If the page request will lead to a limited number of results then apply that limit here.
            if (offset != 0 || length < filtered.size()) {
                final List<E> limited = new ArrayList<>(length);
                for (int i = offset; i < offset + length; i++) {
                    limited.add(filtered.get(i));
                }
                result = new BaseResultList<>(limited, (long) offset, (long) filtered.size(), offset + length < filtered.size());
            }
        }

        if (result == null) {
            result = new BaseResultList<>(filtered, (long) 0, (long) filtered.size(), false);
        }

        return result;
    }
//
//    // TODO : Remove this method when the explorer service is broken out as a separate micro service.
//    @Transactional(readOnly = true)
//    public BaseResultList<E> findInsecure(final C criteria) throws RuntimeException {
//        final List<E> list = findServiceHelper.find(criteria, getSqlFieldMap());
//        return BaseResultList.createCriterialBasedList(list, criteria);
//    }

    private List<E> filterResults(final List<E> list, final String permission) {
        return list.stream().filter(e -> securityContext.hasDocumentPermission(e.getType(), e.getUuid(), permission)).collect(Collectors.toList());
    }
//
//    protected List<EntityReferenceQuery> getReferenceTableList() {
//        return Collections.emptyList();
//    }

    @Override
    public DocRef importDocument(final Folder folder, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        E entity = null;

        try {
            // Get the main config data.
            String mainConfigPath = null;
            for (final String key : dataMap.keySet()) {
                if (key.endsWith(getEntityType() + ".xml")) {
                    mainConfigPath = key;
                }
            }

            if (mainConfigPath == null) {
                throw new RuntimeException("Unable to find config data");
            }

            final Config config = new Config();
            config.read(new StringReader(dataMap.get(mainConfigPath)));

            final String uuid = config.getString("uuid");
            if (uuid == null) {
                throw new RuntimeException("Unable to get UUID for item");
            }

            entity = loadByUuid(uuid, Collections.singleton("all"));

            if (entity == null) {
                entity = getEntityClass().newInstance();
                entity.setFolder(folder);

                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    importState.setState(State.NEW);
                }
            } else {
                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    importState.setState(State.UPDATE);
                }
            }

            importExportHelper.performImport(entity, dataMap, mainConfigPath, importState, importMode);

            // Save directly so there is no marshalling of objects that would destroy imported data.
            if (importMode == ImportMode.IGNORE_CONFIRMATION
                    || (importMode == ImportMode.ACTION_CONFIRMATION && importState.isAction())) {
                entity = getEntityManager().saveEntity(entity);
            }

        } catch (final Exception e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
        }

        return DocRefUtil.create(entity);
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT)) {
            final E entity = entityServiceHelper.loadByUuid(docRef.getUuid(), Collections.emptySet(), queryAppender);
            if (entity != null) {
                return importExportHelper.performExport(entity, omitAuditFields, messageList);
            }
        }

        return Collections.emptyMap();
    }

    @Transient
    @Override
    public String getNamePattern() {
        return StroomProperties.getProperty(NAME_PATTERN_PROPERTY, NAME_PATTERN_VALUE);
    }

//    private String getDocReference(BaseEntity entity) {
//        return "(" + DocRefUtil.create(entity).toString() + ")";
//    }

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
//
//    @Override
//    public String[] getPermissions() {
//        return STANDARD_PERMISSIONS;
//    }


    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public final DocRef createDocument(final String name, final String parentFolderUUID) {
        return DocRefUtil.create(create(name, parentFolderUUID));
    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        return DocRefUtil.create(copy(entity, "Copy of " + entity.getName(), parentFolderUUID));
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        return DocRefUtil.create(move(entity, parentFolderUUID));
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        return DocRefUtil.create(rename(entity, name));
    }

    @Override
    public void deleteDocument(final String uuid) {
        final E entity = loadByUuid(uuid);
        if (entity == null) {
            throw new EntityServiceException("Entity not found");
        }
        delete(entity);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Transactional(readOnly = true)
    @Override
    public E readDocument(final DocRef docRef) {
        return loadByUuid(docRef.getUuid());
    }

    @SuppressWarnings("unchecked")
    @Override
    public E writeDocument(final E document) {
        return save(document);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E forkDocument(final E document, final String name, final DocRef destinationFolderRef) {
        String parentFolderUUID = null;
        if (destinationFolderRef != null) {
            parentFolderUUID = destinationFolderRef.getUuid();
        }

        return copy(document, name, parentFolderUUID);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    protected QueryAppender<E, ?> createQueryAppender(final StroomEntityManager entityManager) {
        return new QueryAppender<>(entityManager);
    }

//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final C criteria) {
//        CriteriaLoggingUtil.appendPageRequest(items, criteria.getPageRequest());
//    }

//    private void checkCreatePermission(final E entity, final DocRef folder) {
//        checkCreatePermission(DocRefUtil.create(entity), folder);
//    }

    private void checkCreatePermission(final String folderUUID) {
        // Only allow administrators to create documents with no folder.
        if (folderUUID == null) {
            if (!securityContext.isAdmin()) {
                throw new PermissionException("Only administrators can create root level entries");
            }
        } else {
            if (!securityContext.hasDocumentPermission(Folder.ENTITY_TYPE, folderUUID, DocumentPermissionNames.getDocumentCreatePermission(getEntityType()))) {
                throw new PermissionException("You do not have permission to create (" + getEntityType() + ") in folder " + folderUUID);
            }
        }
    }

//    private void checkCreatePermission(final DocRef folder) {
//        // Only allow administrators to create documents with no folder.
//        if (folder == null) {
//            if (!securityContext.isAdmin()) {
//                throw new PermissionException("Only administrators can create root level entries");
//            }
//        } else {
//            if (!securityContext.hasDocumentPermission(Folder.ENTITY_TYPE, folder.getUuid(), DocumentPermissionNames.getDocumentCreatePermission(getEntityType()))) {
//                throw new PermissionException("You do not have permission to create (" + getEntityType() + ") in folder " + folder);
//            }
//        }
//    }

    protected void checkUpdatePermission(final E entity) {
        if (!entity.isPersistent()) {
            throw new EntityServiceException("You cannot update an entity that has not been created");
        }
        checkUpdatePermission(DocRefUtil.create(entity));
    }

    private void checkUpdatePermission(final DocRef docRef) {
        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.UPDATE)) {
            throw new PermissionException("You do not have permission to update (" + docRef + ")");
        }
    }

    protected final void checkReadPermission(final E entity) {
        checkReadPermission(DocRefUtil.create(entity));
    }

    protected final void checkReadPermission(final DocRef docRef) {
        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException("You do not have permission to read (" + docRef + ")");
        }
    }

    private void checkDeletePermission(final E entity) {
        checkDeletePermission(DocRefUtil.create(entity));
    }

    protected void checkDeletePermission(final DocRef docRef) {
        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.DELETE)) {
            throw new PermissionException("You do not have permission to delete (" + docRef + ")");
        }
    }

    private void clearDocumentPermissions(final DocRef docRef) {
        String docType = null;
        String docUuid = null;

        if (docRef != null) {
            docType = docRef.getType();
            docUuid = docRef.getUuid();
        }

        securityContext.clearDocumentPermissions(docType, docUuid);
    }

    private void addDocumentPermissions(final DocRef source, final DocRef dest, final boolean owner) {
        String sourceType = null;
        String sourceUuid = null;
        String destType = null;
        String destUuid = null;

        if (source != null) {
            sourceType = source.getType();
            sourceUuid = source.getUuid();
        }

        if (dest != null) {
            destType = dest.getType();
            destUuid = dest.getUuid();
        }

        securityContext.addDocumentPermissions(sourceType, sourceUuid, destType, destUuid, owner);
    }

    protected FieldMap createFieldMap() {
        return new FieldMap()
                .add(BaseCriteria.FIELD_ID, BaseEntity.ID, "id")
                .add(FindNamedEntityCriteria.FIELD_NAME, NamedEntity.NAME, "name")
                .add(FindDocumentEntityCriteria.FIELD_FOLDER, Folder.FOREIGN_KEY, "folder.name");
    }

    final FieldMap getSqlFieldMap() {
        if (sqlFieldMap == null) {
            sqlFieldMap = createFieldMap();
        }
        return sqlFieldMap;
    }
//
//    public static final class EntityReferenceQuery {
//        private final String entityType;
//        private final String tableName;
//        private final String whereClause;
//
//        public EntityReferenceQuery(final String entityType, final String tableName, final String whereClause) {
//            this.entityType = entityType;
//            this.tableName = tableName;
//            this.whereClause = whereClause;
//        }
//
//        public String getEntityType() {
//            return entityType;
//        }
//
//        public String getTableName() {
//            return tableName;
//        }
//
//        public String getWhereClause() {
//            return whereClause;
//        }
//    }
}