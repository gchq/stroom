/*
 * Copyright 2016 Crown Copyright
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

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.query.api.DocRef;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.FindService;
import stroom.entity.shared.Folder;
import stroom.query.api.EntityDocRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class MockDocumentEntityService<E extends DocumentEntity, C extends FindDocumentEntityCriteria> implements DocumentEntityService<E>, FindService<E, C>, Clearable {
    private static final Set<String> BLANK_SET = Collections.emptySet();
    protected final Map<Long, E> map = new ConcurrentHashMap<>();
    private final AtomicLong currentId = new AtomicLong();

    private String entityType;

    @Override
    public E create(final DocRef folder, final String name) throws RuntimeException {
        // Create a new entity instance.
        E entity;
        try {
            entity = getEntityClass().newInstance();
        } catch (final IllegalAccessException | InstantiationException e) {
            throw new EntityServiceException(e.getMessage());
        }

        // Validate the entity name.
        entity.setName(name);
        setFolder(entity, folder);
        return doSave(entity);
    }

    private void setFolder(final E entity, final DocRef folderRef) throws RuntimeException {
        // TODO : Remove this when document entities no longer reference a folder.
        Folder folder = null;
        if (folderRef != null && folderRef instanceof EntityDocRef && ((EntityDocRef)folderRef).getId() != null) {
            folder = new Folder();
            folder.setId(((EntityDocRef)folderRef).getId());
        }
        entity.setFolder(folder);

        if (entity.getUuid() == null) {
            entity.setUuid(UUID.randomUUID().toString());
        }
    }

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

    @Override
    public E loadByName(final DocRef folder, final String name) throws RuntimeException {
        return loadByName(folder, name, null);
    }

    @Override
    public E loadByName(final DocRef folder, final String name, final Set<String> fetchSet) throws RuntimeException {
        final BaseResultList<E> results = find(null);
        if (results == null) {
            return null;
        }

        for (final E entity : results) {
            boolean found = true;
            if (folder != null && !folder.equals(entity.getFolder())) {
                found = false;
            }

            if (name != null && !name.equals(entity.getName())) {
                found = false;
            }

            if (found) {
                return entity;
            }
        }

        return null;
    }

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

    @Override
    public E save(final E entity) throws RuntimeException {
        if (!entity.isPersistent()) {
            throw new EntityServiceException("You cannot update an entity that has not been created");
        }

        if (entity.getUuid() == null) {
            entity.setUuid(UUID.randomUUID().toString());
        }
        return doSave(entity);
    }

    @Override
    public E copy(final E entity, final DocRef folder, final String name) {
        // This is going to be a copy so clear the persistence so save will create a new DB entry.
        entity.clearPersistence();

        // Validate the entity name.
        entity.setName(name);

        setFolder(entity, folder);
        return doSave(entity);
    }

    @Override
    public E move(final E entity, final DocRef folder) {
        setFolder(entity, folder);
        return save(entity);
    }

    private E doSave(final E entity) {
        if (!entity.isPersistent()) {
            entity.setId(currentId.incrementAndGet());
        }
        map.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Boolean delete(E entity) throws RuntimeException {
        if (map.remove(entity.getId()) != null) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public List<E> findByFolder(final DocRef folder, final Set<String> fetchSet) throws RuntimeException {
        final BaseResultList<E> results = find(null);
        if (results == null) {
            return null;
        }

        final List<E> list = new ArrayList<>(results.size());
        for (final E entity : results) {
            if (folder != null && folder.equals(entity.getFolder())) {
                list.add(entity);
            }
        }

        return list;
    }

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

    @Override
    public String[] getPermissions() {
        return new String[0];
    }

    @Override
    public E importEntity(E entity, DocRef folder) {
        // We don't want to overwrite any marshaled data so disable marshalling on creation.
        setFolder(entity, folder);

        // Save directly so there is no marshalling of objects that would destroy imported data.
        return doSave(entity);
    }

    @Override
    public E exportEntity(final E entity) {
        return entity;
    }

    @Override
    public void clear() {
        map.clear();
        currentId.set(0);
    }

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
