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

import event.logging.BaseAdvancedQueryItem;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.FieldMap;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.FindService;
import stroom.entity.shared.Folder;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.ImportState.State;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.PermissionException;
import stroom.entity.shared.PermissionInheritance;
import stroom.importexport.server.Config;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.config.StroomProperties;
import stroom.util.shared.EqualsUtil;
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
public abstract class DocumentEntityServiceImpl<E extends DocumentEntity, C extends FindDocumentEntityCriteria> implements DocumentEntityService<E>, FindService<E, C>, SupportsCriteriaLogging<C> {
    public static final String NAME_PATTERN_PROPERTY = "stroom.namePattern";
    public static final String NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    public static final String ID = "@ID@";
    public static final String TYPE = "@TYPE@";
    public static final String NAME = "@NAME@";

    protected static final String[] STANDARD_PERMISSIONS = new String[]{DocumentPermissionNames.USE,
            DocumentPermissionNames.READ, DocumentPermissionNames.UPDATE, DocumentPermissionNames.DELETE, DocumentPermissionNames.OWNER};

    private final StroomEntityManager entityManager;
    private final SecurityContext securityContext;
    private final EntityServiceHelper<E> entityServiceHelper;
    private final FindServiceHelper<E, C> findServiceHelper;
    private final ImportExportHelper importExportHelper;

    private final QueryAppender<E, C> queryAppender;

    private String entityType;
    private FieldMap sqlFieldMap;

    protected DocumentEntityServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext) {
        this.entityManager = entityManager;
        this.importExportHelper = importExportHelper;
        this.securityContext = securityContext;
        this.queryAppender = createQueryAppender(entityManager);
        this.entityServiceHelper = new EntityServiceHelper<>(entityManager, getEntityClass(), queryAppender);
        this.findServiceHelper = new FindServiceHelper<>(entityManager, getEntityClass(), queryAppender);
    }

    public StroomEntityManager getEntityManager() {
        return entityManager;
    }

    public EntityServiceHelper<E> getEntityServiceHelper() {
        return entityServiceHelper;
    }

    @Override
    public E create(final DocRef folder, final String name) throws RuntimeException {
        return create(folder, name, PermissionInheritance.NONE);
    }

    @Override
    public E create(final DocRef folder, final String name, final PermissionInheritance permissionInheritance) throws RuntimeException {
        // Create a new entity instance.
        E entity;
        try {
            entity = getEntityClass().newInstance();
        } catch (final IllegalAccessException | InstantiationException e) {
            throw new EntityServiceException(e.getMessage());
        }

        entity.setName(name);
        setFolder(entity, folder);
        final E result = entityServiceHelper.create(entity);
        final DocRef dest = DocRef.create(result);

        // Create the initial user permissions for this new document.
        switch (permissionInheritance) {
            case NONE:
                addDocumentPermissions(null, dest, true);
                break;
            case COMBINED:
                addDocumentPermissions(folder, dest, true);
                break;
            case INHERIT:
                addDocumentPermissions(folder, dest, true);
                break;
        }

        return result;
    }

    // TODO : Temporary for query service.
    protected E create(final E entity) {
        return entityServiceHelper.create(entity);
    }

    private void setFolder(final E entity, final DocRef folderRef) throws RuntimeException {
        if (entity.getUuid() == null) {
            entity.setUuid(UUID.randomUUID().toString());
        }

        // TODO : Remove this when document entities no longer reference a folder.
        Folder folder = null;
        if (folderRef != null && folderRef.getId() != null) {
            folder = new Folder();
            folder.setId(folderRef.getId());
        }
        entity.setFolder(folder);

        checkCreatePermission(entity, folderRef);
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
        return loadById(entity.getId(), fetchSet);
    }

    @Transactional(readOnly = true)
    @Override
    public E loadById(final long id) throws RuntimeException {
        return loadById(id, Collections.emptySet());
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    @Override
    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
        E entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");

        queryAppender.appendBasicJoin(sql, "e", fetchSet);

        sql.append(" WHERE e.id = ");
        sql.arg(id);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql, null, true);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null) {
            queryAppender.postLoad(entity);
            checkReadPermission(entity);
        }

        return entity;
    }

    // TODO : Remove this method when the explorer service is broken out as a separate micro service.
    @Transactional(readOnly = true)
    @Override
    public E loadByIdInsecure(final long id, final Set<String> fetchSet) throws RuntimeException {
        E entity = null;

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");

        queryAppender.appendBasicJoin(sql, "e", fetchSet);

        sql.append(" WHERE e.id = ");
        sql.arg(id);

        final List<E> resultList = getEntityManager().executeQueryResultList(sql, null, true);
        if (resultList != null && resultList.size() > 0) {
            entity = resultList.get(0);
        }

        if (entity != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

    @Transactional(readOnly = true)
    @Override
    public final E loadByUuid(final String uuid) throws RuntimeException {
        return loadByUuid(uuid, null);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    @Override
    public final E loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        queryAppender.appendBasicJoin(sql, "e", fetchSet);
        sql.append(" WHERE e.uuid = ");
        sql.arg(uuid);

        final BaseResultList<E> list = BaseResultList.createUnboundedList(entityManager.executeQueryResultList(sql, null, true));
        final E entity = list.getFirst();

        if (entity != null) {
            queryAppender.postLoad(entity);
            checkReadPermission(entity);
        }

        return entity;
    }

    // TODO : Remove this method when the explorer service is broken out as a separate micro service.
    @Transactional(readOnly = true)
    public final E loadByUuidInsecure(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        queryAppender.appendBasicJoin(sql, "e", fetchSet);
        sql.append(" WHERE e.uuid = ");
        sql.arg(uuid);

        final BaseResultList<E> list = BaseResultList.createUnboundedList(entityManager.executeQueryResultList(sql, null, true));
        final E entity = list.getFirst();

        if (entity != null) {
            queryAppender.postLoad(entity);
        }

        return entity;
    }

    @Transactional(readOnly = true)
    @Override
    public final E loadByName(final DocRef folder, final String name) throws RuntimeException {
        return loadByName(folder, name, null);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    @Override
    public final E loadByName(final DocRef folder, final String name, final Set<String> fetchSet) throws RuntimeException {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        queryAppender.appendBasicJoin(sql, "e", fetchSet);
        sql.append(" WHERE e.name = ");
        sql.arg(name);

        final Class<?> clazz = getEntityClass();
        if (DocumentEntity.class.isAssignableFrom(clazz)) {
            // For some reason this doesn't work on folders themselves?
            if (!getEntityClass().equals(Folder.class)) {
                if (folder == null) {
                    sql.append(" AND e.folder IS NULL");
                } else {
                    sql.append(" AND e.folder.uuid = ");
                    sql.arg(folder.getUuid());
                }
            }
        }

        final BaseResultList<E> list = BaseResultList.createUnboundedList(entityManager.executeQueryResultList(sql, null, true));

        // FIXME: Fix once folders have been removed from entities. For now filter by parent group id manually
        E entity = null;
        if (getEntityClass().equals(Folder.class)) {
            for (final E e : list) {
                if (folder == null) {
                    if (e.getFolder() == null) {
                        entity = e;
                        break;
                    }
                } else {
                    if (e.getFolder() != null && EqualsUtil.isEquals(folder.getUuid(), e.getFolder().getUuid())) {
                        entity = e;
                        break;
                    }
                }
            }
        } else {
            entity = list.getFirst();
        }

        if (entity != null) {
            queryAppender.postLoad(entity);
            checkReadPermission(entity);
        }

        return entity;
    }

    @Override
    public E save(final E entity) throws RuntimeException {
        checkUpdatePermission(entity);

        if (entity.getUuid() == null) {
            entity.setUuid(UUID.randomUUID().toString());
        }
        return entityServiceHelper.save(entity);
    }

    @Override
    public E copy(final E entity, final DocRef folder, final String name, final PermissionInheritance permissionInheritance) {
        final DocRef source = DocRef.create(entity);

        // Check that we can read the entity that we are going to copy.
        checkReadPermission(entity);

        // This is going to be a copy so clear the persistence so save will create a new DB entry.
        entity.clearPersistence();

        entity.setName(name);

        setFolder(entity, folder);

        final E result = entityServiceHelper.create(entity);
        final DocRef dest = DocRef.create(result);

        if (permissionInheritance != null) {
            switch (permissionInheritance) {
                case NONE:
                    addDocumentPermissions(source, dest, true);
                    break;
                case COMBINED:
                    addDocumentPermissions(source, dest, true);
                    addDocumentPermissions(folder, dest, true);
                    break;
                case INHERIT:
                    addDocumentPermissions(folder, dest, true);
                    break;
            }
        }

        return result;
    }

    @Override
    public E move(final E entity, final DocRef folder, final PermissionInheritance permissionInheritance) {
        // Check that we can read the entity that we are going to move.
        checkReadPermission(entity);

        setFolder(entity, folder);

        final E result = save(entity);
        final DocRef dest = DocRef.create(result);

        if (permissionInheritance != null) {
            switch (permissionInheritance) {
                case NONE:
                    break;
                case COMBINED:
                    addDocumentPermissions(folder, dest, false);
                    break;
                case INHERIT:
                    clearDocumentPermissions(dest);
                    addDocumentPermissions(folder, dest, false);
                    break;
            }
        }

        return result;
    }

    @Override
    public Boolean delete(final E entity) throws RuntimeException {
        checkDeletePermission(entity);
        return entityManager.deleteEntity(entity);
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<E> findByFolder(final DocRef folder, final Set<String> fetchSet) throws RuntimeException {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");

        queryAppender.appendBasicJoin(sql, "e", fetchSet);

        sql.append(" WHERE 1=1");

        if (folder != null) {
            sql.append(" AND e.folder.uuid = ");
            sql.arg(folder.getUuid());
        }

        final List<E> list = entityManager.executeQueryResultList(sql);
        return filterResults(list, DocumentPermissionNames.READ);
    }

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

    // TODO : Remove this method when the explorer service is broken out as a separate micro service.
    @Transactional(readOnly = true)
    public BaseResultList<E> findInsecure(final C criteria) throws RuntimeException {
        final List<E> list = findServiceHelper.find(criteria, getSqlFieldMap());
        return BaseResultList.createCriterialBasedList(list, criteria);
    }

    private List<E> filterResults(final List<E> list, final String permission) {
        return list.stream().filter(e -> securityContext.hasDocumentPermission(e.getType(), e.getUuid(), permission)).collect(Collectors.toList());
    }

    protected List<EntityReferenceQuery> getReferenceTableList() {
        return Collections.emptyList();
    }

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

            // We don't want to overwrite any marshaled data so disable marshalling on creation.
            setFolder(entity, DocRef.create(folder));

            // Save directly so there is no marshalling of objects that would destroy imported data.
            if (importMode == ImportMode.IGNORE_CONFIRMATION
                    || (importMode == ImportMode.ACTION_CONFIRMATION && importState.isAction())) {
                entity = getEntityManager().saveEntity(entity);
            }

        } catch (final Exception e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
        }

        return DocRef.create(entity);
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT)) {
            final E entity = entityServiceHelper.loadByUuid(docRef.getUuid());
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

    private String getDocReference(BaseEntity entity) {
        return "(" + DocRef.create(entity).toString() + ")";
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

    @Override
    public String[] getPermissions() {
        return STANDARD_PERMISSIONS;
    }

    protected QueryAppender<E, C> createQueryAppender(final StroomEntityManager entityManager) {
        return new QueryAppender(entityManager);
    }

    protected final QueryAppender<E, C> getQueryAppender() {
        return queryAppender;
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final C criteria) {
        CriteriaLoggingUtil.appendPageRequest(items, criteria.getPageRequest());
    }

    private void checkCreatePermission(final E entity, final DocRef folder) {
        // Only allow administrators to create documents with no folder.
        if (folder == null) {
            if (!securityContext.isAdmin()) {
                throw new PermissionException("Only administrators can create root level entries");
            }
        } else {
            if (!securityContext.hasDocumentPermission(Folder.ENTITY_TYPE, folder.getUuid(), DocumentPermissionNames.getDocumentCreatePermission(getEntityType()))) {
                throw new PermissionException("You do not have permission to create " + getDocReference(entity) + " in folder " + folder);
            }
        }
    }

    protected void checkUpdatePermission(final E entity) {
        if (!entity.isPersistent()) {
            throw new EntityServiceException("You cannot update an entity that has not been created");
        }

        if (!securityContext.hasDocumentPermission(entity.getType(), entity.getUuid(), DocumentPermissionNames.UPDATE)) {
            throw new PermissionException("You do not have permission to update " + getDocReference(entity));
        }
    }

    protected final void checkReadPermission(final E entity) {
        if (!securityContext.hasDocumentPermission(entity.getType(), entity.getUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException("You do not have permission to read " + getDocReference(entity));
        }
    }

    protected final void checkDeletePermission(final E entity) {
        if (!securityContext.hasDocumentPermission(entity.getType(), entity.getUuid(), DocumentPermissionNames.DELETE)) {
            throw new PermissionException("You do not have permission to delete " + getDocReference(entity));
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

    public static final class EntityReferenceQuery {
        private final String entityType;
        private final String tableName;
        private final String whereClause;

        public EntityReferenceQuery(final String entityType, final String tableName, final String whereClause) {
            this.entityType = entityType;
            this.tableName = tableName;
            this.whereClause = whereClause;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getTableName() {
            return tableName;
        }

        public String getWhereClause() {
            return whereClause;
        }
    }
}
