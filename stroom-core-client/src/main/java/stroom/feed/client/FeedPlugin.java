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

package stroom.feed.client;

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntityPlugin;
import stroom.entity.client.EntityPluginEventManager;
import stroom.entity.client.presenter.EntityEditPresenter;
import stroom.feed.client.presenter.FeedPresenter;
import stroom.feed.shared.Feed;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.StreamType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FeedPlugin extends EntityPlugin<Feed> {
    public static final Set<String> FETCH_SET = new HashSet<String>(Arrays.asList(StreamType.ENTITY_TYPE));
    private final Provider<FeedPresenter> editorProvider;

    @Inject
    public FeedPlugin(final EventBus eventBus, final Provider<FeedPresenter> editorProvider,
                      final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext,
                      final ContentManager contentManager, final EntityPluginEventManager entityPluginEventManager) {
        super(eventBus, dispatcher, securityContext, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
    }

    @Override
    protected EntityEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public String getType() {
        return Feed.ENTITY_TYPE;
    }

    @Override
    protected Set<String> fetchSet() {
        return FETCH_SET;
    }

    public interface FeedResources extends ClientBundle {
        ImageResource eventFeed();
    }
}
