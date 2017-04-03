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

import stroom.entity.shared.Entity;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindService;
import stroom.entity.shared.HasLoadById;
import stroom.entity.shared.HasLoadByName;
import stroom.entity.shared.HasLoadByUuid;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GenericEntityServiceImpl implements GenericEntityService {
    private static final Set<String> NO_FETCH_SET = new HashSet<>();
    private final EntityServiceBeanRegistry entityServiceBeanRegistry;

    @Inject
    public GenericEntityServiceImpl(EntityServiceBeanRegistry entityServiceBeanRegistry) {
        this.entityServiceBeanRegistry = entityServiceBeanRegistry;
    }

    @Override
    public <E extends Entity> E load(final E entity) {
        return load(entity, NO_FETCH_SET);
    }

    @Override
    public <E extends Entity> E load(final E entity, final Set<String> fetchSet) {
        if (entity == null) {
            return null;
        } else if (!entity.isPersistent()) {
            return entity;
        }

        final EntityService<E> entityService = getEntityService(entity.getType());
        return entityService.load(entity, fetchSet);
    }

    @Override
    public <E extends Entity> E loadById(final String entityType, final long id) {
        return loadById(entityType, id, NO_FETCH_SET);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Entity> E loadById(final String entityType, final long id, final Set<String> fetchSet) {
        final EntityService<E> entityService = getEntityService(entityType);
        if (entityService instanceof HasLoadById<?>) {
            final HasLoadById<?> hasLoadById = (HasLoadById<?>) entityService;
            return (E) hasLoadById.loadById(id, fetchSet);
        }

        return null;
    }

    @Override
    public <E extends Entity> E loadByUuid(final String entityType, final String uuid) {
        return loadByUuid(entityType, uuid, NO_FETCH_SET);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Entity> E loadByUuid(final String entityType, final String uuid, final Set<String> fetchSet) {
        final EntityService<E> entityService = getEntityService(entityType);
        if (entityService instanceof HasLoadByUuid<?>) {
            final HasLoadByUuid<?> hasLoadByUuid = (HasLoadByUuid<?>) entityService;
            return (E) hasLoadByUuid.loadByUuid(uuid, fetchSet);
        }

        return null;
    }

    @Override
    public <E extends Entity> E loadByName(final String entityType, final DocRef folder, final String name) {
        return loadByName(entityType, folder, name, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Entity> E loadByName(final String entityType, final DocRef folder, final String name,
                                                   final Set<String> fetchSet) {
        final EntityService<E> entityService = getEntityService(entityType);
        if (entityService instanceof HasLoadByName) {
            return ((HasLoadByName<E>) entityService).loadByName(name, fetchSet);
        }

        if (entityService instanceof DocumentEntityService) {
            return (E) ((DocumentEntityService<?>) entityService).loadByName(folder, name, fetchSet);
        }

        throw new EntityServiceException(
                "Entity service is not an instance of NamedEntityService or DocumentEntityService: " + entityType, null,
                false);
    }

    @Override
    public <E extends Entity> E loadByName(final String entityType, final String name) {
        return loadByName(entityType, name, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Entity> E loadByName(final String entityType, final String name,
                                                   final Set<String> fetchSet) {
        final EntityService<E> entityService = getEntityService(entityType);
        if (!(entityService instanceof HasLoadByName)) {
            throw new EntityServiceException("Entity service is not an instance of HasLoadByName: " + entityType,
                    null, false);
        }

        final HasLoadByName<E> hasLoadByName = (HasLoadByName<E>) entityService;
        return hasLoadByName.loadByName(name, fetchSet);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Entity, C extends BaseCriteria> BaseResultList<E> find(final String entityType,
                                                                                     final C criteria) {
        final EntityService<E> entityService = getEntityService(entityType);
        final FindService<E, C> findService = (FindService<E, C>) entityService;
        return findService.find(criteria);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends DocumentEntity> List<E> findByFolder(final String entityType,
                                                           final DocRef folder, final Set<String> fetchSet) {
        final EntityService<E> entityService = getEntityService(entityType);
        if (!(entityService instanceof DocumentEntityService)) {
            throw new EntityServiceException("Entity service does not implement findByFolder() " + entityType, null,
                    false);
        }

        final DocumentEntityService<E> findService = (DocumentEntityService<E>) entityService;
        return findService.findByFolder(folder, fetchSet);
    }

    @Override
    public Collection<DocumentEntityService<?>> findAll() {
        final Collection<Object> services = entityServiceBeanRegistry
                .getAllServicesByParent(DocumentEntityService.class);
        final List<DocumentEntityService<?>> list = new ArrayList<>(services.size());
        list.addAll(services.stream().map(service -> (DocumentEntityService<?>) service).collect(Collectors.toList()));
        return list;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Entity> E save(final E entity) {
        if (entity == null) {
            return null;
        }
        return (E) getEntityService(entity.getType()).save(entity);
    }

//    @SuppressWarnings("unchecked")
//    @Override
//    public <E extends DocumentEntity> E saveAs(final E entity, final DocRef folder, final String name) {
//        if (entity == null) {
//            return null;
//        }
//        final EntityService<E> entityService = getEntityService(entity.getType());
//        if (entityService instanceof DocumentEntityService) {
//            return ((DocumentEntityService<E>) entityService).copy(entity, folder, name);
//        }
//
//        return entity;
//    }

    @Override
    public <E extends Entity> Boolean delete(final E entity) {
        if (entity == null) {
            return Boolean.FALSE;
        }
        return getEntityService(entity.getType()).delete(entity);
    }

//    @SuppressWarnings("unchecked")
//    @Override
//    public <E extends DocumentEntity> E importEntity(final E entity, final DocRef folder) {
//        if (entity == null) {
//            return null;
//        }
//        final EntityService<E> entityService = getEntityService(entity.getType());
//        if (entityService instanceof DocumentEntityService) {
//            return ((DocumentEntityService<E>) entityService).importEntity(entity, folder);
//        }
//
//        return entity;
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public <E extends DocumentEntity> E exportEntity(final E entity) {
//        if (entity == null) {
//            return null;
//        }
//        final EntityService<E> entityService = getEntityService(entity.getType());
//        if (entityService instanceof DocumentEntityService) {
//            return ((DocumentEntityService<E>) entityService).exportEntity(entity);
//        }
//
//        return entity;
//    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends Entity> EntityService<E> getEntityService(
            final String entityType) {
        final Object entityService = entityServiceBeanRegistry.getEntityService(entityType);
        if (entityService == null || !(entityService instanceof EntityService)) {
            throw new EntityServiceException("Cannot find entity service for " + entityType, null, false);
        }

        return (EntityService<E>) entityService;
    }

//    @SuppressWarnings("unchecked")
//    @Override
//    public <E extends Entity, C extends BaseCriteria> FindService<E, C> getFindService(String entityType) {
//        final Object entityService = entityServiceBeanRegistry.getEntityService(entityType);
//        if (entityService == null || !(entityService instanceof FindService)) {
//            throw new EntityServiceException("Cannot find 'find' service for " + entityType, null, false);
//        }
//
//        return (FindService<E, C>) entityService;
//    }
}
