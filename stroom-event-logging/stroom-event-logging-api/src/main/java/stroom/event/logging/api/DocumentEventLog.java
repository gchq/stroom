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

package stroom.event.logging.api;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageResponse;

import event.logging.ProcessEventAction;
import event.logging.Query;
import event.logging.SearchEventAction;

public interface DocumentEventLog {

    void copy(final Object before, final Object after, final String eventTypeId, final String verb, final Throwable ex);

    void copy(final Object before, final Object after, final String eventTypeId, final Throwable ex);

    void copy(final Object before, final Object after, final Throwable ex);

    void create(final String entityType,
                final String entityName,
                final String eventTypeId,
                final String verb,
                final Throwable ex);

    void create(final String entityType, final String entityName, final String eventTypeId, final Throwable ex);

    void create(final String entityType, final String entityName, final Throwable ex);

    void create(final Object entity, final String eventTypeId, final String verb, final Throwable ex);

    void create(final Object entity, final String eventTypeId, final Throwable ex);

    void create(final Object entity, final Throwable ex);

    void delete(final BaseCriteria criteria,
                final Query query,
                final Long size,
                final String eventTypeId,
                final String verb,
                final Throwable ex);

    void delete(final BaseCriteria criteria,
                final Query query,
                final Long size,
                final String eventTypeId,
                final Throwable ex);

    void delete(final BaseCriteria criteria, final Query query, final Long size, final Throwable ex);

    void delete(final Object entity, final String eventTypeId, final String verb, final Throwable ex);

    void delete(final Object entity, final String eventTypeId, final Throwable ex);

    void delete(final Object entity, final Throwable ex);

    void download(final Object entity, final String eventTypeId, final String verb, final Throwable ex);

    void download(final Object entity, final String eventTypeId, final Throwable ex);

    void download(final Object entity, final Throwable ex);

    void move(final Object before, final Object after, final String eventTypeId, final String verb, final Throwable ex);

    void move(final Object before, final Object after, final String eventTypeId, final Throwable ex);

    void move(final Object before, final Object after, final Throwable ex);

    void process(final Object entity, final String eventTypeId, final String description, final Throwable ex,
                 final EventActionDecorator<ProcessEventAction> actionDecorator);

    void process(final Object entity, final String eventTypeId, final String description, final Throwable ex);

    void process(final Object entity, final String eventTypeId, final Throwable ex);

    void rename(final Object before,
                final Object after,
                final String eventTypeId,
                final String verb,
                final Throwable ex);

    void rename(final Object before, final Object after, final String eventTypeId, final Throwable ex);

    void rename(final Object before, final Object after, final Throwable ex);

    void search(final String typeId, final Query query, final String resultType, final PageResponse pageResponse,
                final String verb, final Throwable ex, final EventActionDecorator<SearchEventAction> actionDecorator);

    void search(final String typeId,
                final Query query,
                final String resultType,
                final PageResponse pageResponse,
                final String verb,
                final Throwable ex);

    void search(final String typeId,
                final Query query,
                final String resultType,
                final PageResponse pageResponse,
                final Throwable ex);

    void unknownOperation(final Object entity, final String eventTypeId, String description, Throwable ex);

    void update(final Object before, final Object after, String eventTypeId, String verb, Throwable ex);

    void update(final Object before, final Object after, String eventTypeId, Throwable ex);

    void update(final Object before, final Object after, Throwable ex);

    void upload(final java.lang.Object object, final String eventTypeId, String verb, final Throwable ex);

    void upload(final java.lang.Object object, final String eventTypeId, final Throwable ex);

    void upload(final Object object, final Throwable ex);

    void view(final Object entity, final String eventTypeId, String verb, Throwable ex);

    void view(final Object entity, final String eventTypeId, Throwable ex);

    void view(final Object entity, final Throwable ex);
}
