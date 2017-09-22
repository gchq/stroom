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

package stroom.entity.client;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.NamedEntity;
import stroom.query.api.v2.DocRef;

public abstract class EntityPlugin<E extends NamedEntity> extends DocumentPlugin<E> {
    @Inject
    public EntityPlugin(final EventBus eventBus,
                        final ClientDispatchAsync dispatcher,
                        final ContentManager contentManager,
                        final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, dispatcher, contentManager, entityPluginEventManager);
    }

    @Override
    protected DocRef getDocRef(final E document) {
        return DocRefUtil.create(document);
    }
}