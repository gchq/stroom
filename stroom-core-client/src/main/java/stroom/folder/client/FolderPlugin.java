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

package stroom.folder.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.EntityPlugin;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.Folder;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;

public class FolderPlugin extends EntityPlugin<Folder> {
    private final Provider<FolderPresenter> editorProvider;
    private final ClientSecurityContext securityContext;

    @Inject
    public FolderPlugin(final EventBus eventBus,
                        final Provider<FolderPresenter> editorProvider,
                        final ClientDispatchAsync dispatcher,
                        final ClientSecurityContext securityContext,
                        final ContentManager contentManager,
                        final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, dispatcher, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
        this.securityContext = securityContext;
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        if (securityContext.hasAppPermission(Stream.VIEW_DATA_PERMISSION) || securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)) {
            return editorProvider.get();
        }

        return null;
    }

    @Override
    public String getType() {
        return Folder.ENTITY_TYPE;
    }
}
