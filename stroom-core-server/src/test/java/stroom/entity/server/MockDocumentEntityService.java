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

package stroom.entity.server;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.importexport.server.ImportExportHelper;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.importexport.shared.ImportState.State;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class MockDocumentEntityService<E extends DocumentEntity, C extends FindDocumentEntityCriteria> implements DocumentEntityService<E>, BaseEntityService<E>, FindService<E, C>, Clearable {
    private static final Set<String> BLANK_SET = Collections.emptySet();
    protected final Map<Long, E> map = new ConcurrentHashMap<>();
    private final AtomicLong currentId = new AtomicLong();
    private final ImportExportHelper importExportHelper;

    private String entityType;

    public MockDocumentEntityService(final ImportExportHelper importExportHelper) {
        this.importExportHelper = importExportHelper;
    }

    public MockDocumentEntityService() {
        this.importExportHelper = null;
    }

    @Override
    public E create(final String name) throws RuntimeException {
        // Create a new entity instance.
        E entity;
        try {
            entity = getEntityClass().newInstance();
        } catch (final IllegalAccessException | InstantiationException e) {
            throw new EntityServiceException(e.getMessage());
        }

        entity.setName(name);
        return doSave(entity);
    }
//
//    // =======================
//    // START DocumentService
//    // =======================
//    @Override
//    public DocRef createDocument(final DocRef folder, final String name) {
//        return DocRefUtil.create(create(folder, name));
//    }
//
//    @Override
//    public DocRef copyDocument(final DocRef document, final DocRef folder, final String name) {
//        final E original = loadByUuid(document.getUuid());
//        return DocRefUtil.create(copy(original, folder, name));
//    }
//
//    @Override
//    public DocRef moveDocument(final DocRef document, final DocRef folder, final String name) {
//        final E before = loadByUuid(document.getUuid());
//        return DocRefUtil.create(move(before, folder, name));
//    }
//
//    @Override
//    public Boolean deleteDocument(final DocRef document) {
//        final E entity = loadByUuid(document.getUuid());
//        if (entity != null) {
//            return delete(entity);
//        }
//
//        // If we couldn't find the entity then it must have been deleted already so return true.
//        return true;
//    }
//
//    @Override
//    public DocRef importDocument(final DocRef folder, final String name, final String data) {
//        return null;
//    }
//
//    @Override
//    public String exportDocument(final DocRef document) {
//        return null;
//    }
//
////    @Override
////    public DocumentType getDocumentType() {
////        return null;
////    }
//
//    protected DocumentType getDocumentType(final int priority, final String type, final String displayType) {
//        final String url = getIconUrl(type);
//        return new DocumentType(priority, type, displayType, url);
//    }
//
//    private String getIconUrl(final String type) {
//        return DocumentType.DOC_IMAGE_URL + type + ".png";
//    }
//
//    // =======================
//    // END DocumentService
//    // =======================

//    @Override
//    public E create(final DocRef folder, final String name) {
//        return create(folder, name, PermissionInheritance.NONE);
//    }
//
//    private E create(final DocRef folder, final String name, final PermissionInheritance permissionInheritance) throws RuntimeException {
//        // Create a new entity instance.
//        E entity;
//        try {
//            entity = getEntityClass().newInstance();
//        } catch (final IllegalAccessException | InstantiationException e) {
//            throw new EntityServiceException(e.getMessage());
//        }
//
//        // Validate the entity name.
//        entity.setName(name);
//        setFolder(entity, folder);
//        return doSave(entity);
//    }
//
//    // TODO : Temporary for query service.
//    protected E create(final E entity) {
//        return doSave(entity);
//    }
//
//    @Override
//    public E saveAs(final E entity, final DocRef folder, final String name, final PermissionInheritance permissionInheritance) throws RuntimeException {
//        entity.clearPersistence();
//        entity.setName(name);
//        setFolder(entity, folder);
//        final E result = create(entity);
//        return result;
//    }
//
//    private void setFolder(final E entity, final String folderUUID) throws RuntimeException {
//        DocRef folderRef = null;
//        if (folderUUID != null) {
//            folderRef = new DocRef(Folder.ENTITY_TYPE, folderUUID);
//        }
//
//        setFolder(entity, folderRef);
//    }
//
//    private void setFolder(final E entity, final DocRef folderRef) throws RuntimeException {
//        // TODO : Remove this when document entities no longer reference a folder.
//        Folder folder = null;
//        if (folderRef != null && folderRef.getId() != null) {
//            folder = new Folder();
//            folder.setId(folderRef.getId());
//        }
//        entity.setFolder(folder);
//
//        if (entity.getUuid() == null) {
//            entity.setUuid(UUID.randomUUID().toString());
//        }
//    }

    @Override
    public E loadByUuid(final String uuid) throws RuntimeException {
        return loadByUuid(uuid, null);
    }

    @Override
    public E loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        final List<E> list = find(null);
        for (final E e : list) {
            if (e.getUuid() != null && e.getUuid().equals(uuid)) {
                return e;
            }
        }

        return null;
    }

//    @Override
//    public E loadByName(final DocRef folder, final String name) throws RuntimeException {
//        return loadByName(folder, name, null);
//    }
//
//    @Override
//    public E loadByName(final DocRef folder, final String name, final Set<String> fetchSet) throws RuntimeException {
//        final BaseResultList<E> results = find(null);
//        if (results == null) {
//            return null;
//        }
//
//        for (final E entity : results) {
//            boolean found = true;
//            if (folder != null && !folder.equals(DocRefUtil.create(entity.getFolder()))) {
//                found = false;
//            }
//
//            if (name != null && !name.equals(entity.getName())) {
//                found = false;
//            }
//
//            if (found) {
//                return entity;
//            }
//        }
//
//        return null;
//    }

    @Override
    public E load(final E entity) throws RuntimeException {
        return load(entity, BLANK_SET);
    }

    @Override
    public E load(final E entity, final Set<String> fetchSet) throws RuntimeException {
        if (entity == null) {
            return null;
        }
        return loadById(entity.getId(), fetchSet);
    }

    @Override
    public E loadById(final long id) throws RuntimeException {
        return loadById(id, BLANK_SET);
    }

    @Override
    public E loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
        return map.get(id);
    }

//    @Override
//    public E loadByIdInsecure(final long id, final Set<String> fetchSet) throws RuntimeException {
//        return map.get(id);
//    }

    @Override
    public E save(final E entity) throws RuntimeException {
        if (!entity.isPersistent()) {
            throw new EntityServiceException("You cannot update an entity that has not been created");
        }
        return doSave(entity);
    }

//    @Override
//    public E copy(final E entity, final DocRef folder, final String name) {
//        // This is going to be a copy so clear the persistence so save will create a new DB entry.
//        entity.clearPersistence();
//
//        // Validate the entity name.
//        entity.setName(name);
//
//        setFolder(entity, folder);
//        return doSave(entity);
//    }
//
//    @Override
//    public E move(final E entity, final DocRef folder) {
//        setFolder(entity, folder);
//        return save(entity);
//    }

    private E doSave(final E entity) {
        if (!entity.isPersistent()) {
            entity.setId(currentId.incrementAndGet());
        }
        if (entity.getUuid() == null) {
            entity.setUuid(UUID.randomUUID().toString());
        }
        map.put(entity.getId(), entity);
        return entity;
    }
//
//    @Override
//    public Boolean delete(final DocRef item) {
//        return delete(loadByUuid(item.getUuid()));
//    }

    @Override
    public Boolean delete(E entity) throws RuntimeException {
        if (map.remove(entity.getId()) != null) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

//    @Override
//    public List<E> findByFolder(final DocRef folder, final Set<String> fetchSet) throws RuntimeException {
//        final BaseResultList<E> results = find(null);
//        if (results == null) {
//            return null;
//        }
//
//        final List<E> list = new ArrayList<>(results.size());
//        for (final E entity : results) {
//            if (folder != null && folder.equals(DocRefUtil.create(entity.getFolder()))) {
//                list.add(entity);
//            }
//        }
//
//        return list;
//    }

    public boolean isMatch(final C criteria, final E entity) {
        return criteria.getName().isMatch(entity.getName());
    }

    @Override
    public BaseResultList<E> find(final C criteria) throws RuntimeException {
        final List<E> list = new ArrayList<>();
        for (final E entity : map.values()) {
            if (criteria == null || isMatch(criteria, entity)) {
                list.add(entity);
            }
        }
        return BaseResultList.createUnboundedList(list);
    }

    @Override
    public C createCriteria() {
        return null;
    }

//    @Override
//    public String[] getPermissions() {
//        return new String[0];
//    }

    @Override
    public DocRef importDocument(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        if (importExportHelper == null) {
            throw new RuntimeException("Import not supported for this test");
        }

        E entity = null;

        try {
            // See if a document already exists with this uuid.
            entity = loadByUuid(docRef.getUuid(), Collections.singleton("all"));

            if (entity == null) {
                entity = getEntityClass().newInstance();

                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    importState.setState(State.NEW);
                }
            } else {
                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    importState.setState(State.UPDATE);
                }
            }

            importExportHelper.performImport(entity, dataMap, importState, importMode);

            // Save directly so there is no marshalling of objects that would destroy imported data.
            if (importMode == ImportMode.IGNORE_CONFIRMATION
                    || (importMode == ImportMode.ACTION_CONFIRMATION && importState.isAction())) {
                entity = doSave(entity);
            }

        } catch (final Exception e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
        }

        return DocRefUtil.create(entity);
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return null;
    }

    @Override
    public void clear() {
        map.clear();
        currentId.set(0);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public final DocRef createDocument(final String name, final String parentFolderUUID) {
        return DocRefUtil.create(create(name));
    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        final E entity = loadByUuid(uuid);

        // This is going to be a copy so clear the persistence so save will create a new DB entry.
        entity.clearPersistence();

        final E result = doSave(entity);
        return DocRefUtil.create(result);
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        final E entity = loadByUuid(uuid);

        final E result = save(entity);
        return DocRefUtil.create(result);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        final E entity = loadByUuid(uuid);

        entity.setName(name);

        final E result = save(entity);
        return DocRefUtil.create(result);
    }

    //    @Override
//    public DocRef move(final DocRef document, final DocRef folder, final String name, final PermissionInheritance permissionInheritance) {
//        try {
//            final E entity = loadByUuid(document.getUuid());
//
//            entity.setName(name);
//
//            setFolder(entity, folder);
//
//            final E result = entityServiceHelper.save(entity);
//            final DocRef dest = DocRefUtil.create(result);
//
//            if (permissionInheritance != null) {
//                switch (permissionInheritance) {
//                    case NONE:
//                        addDocumentPermissions(document, dest, true);
//                        break;
//                    case COMBINED:
//                        addDocumentPermissions(document, dest, true);
//                        addDocumentPermissions(folder, dest, true);
//                        break;
//                    case INHERIT:
//                        addDocumentPermissions(folder, dest, true);
//                        break;
//                }
//            }
//
//            documentEventLog.move(document, folder, name);
//            return dest;
//        } catch (final RuntimeException e) {
//            documentEventLog.move(document, folder, name, e);
//            throw e;
//        }
//    }

    @Override
    public void deleteDocument(final String uuid) {
        E entity = loadByUuid(uuid);
        if (entity != null) {
            delete(entity);
        }
    }

    @Override
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
    public String getNamePattern() {
        return null;
    }
}
