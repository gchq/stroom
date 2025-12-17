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

package stroom.data.client;

import stroom.core.client.ContentManager;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.pipeline.shared.SourceLocation;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Optional;
import javax.inject.Singleton;

@Singleton
public class SourceTabPlugin extends AbstractTabPresenterPlugin<SourceKey, SourceTabPresenter> {

    @Inject
    public SourceTabPlugin(final EventBus eventBus,
                           final ContentManager contentManager,
                           final Provider<SourceTabPresenter> sourceTabPresenterProvider) {
        super(eventBus, contentManager, sourceTabPresenterProvider);
    }

    @Override
    protected String getName() {
        return "Source";
    }

    /**
     * 4. This method will open the source and show it in the content pane.
     */
    public Optional<SourceTabPresenter> open(final SourceLocation sourceLocation,
                                             final boolean forceOpen) {

        return super.openTabPresenter(
                forceOpen,
                new SourceKey(sourceLocation),
                sourceTabPresenter ->
                        sourceTabPresenter.setSourceLocationUsingHighlight(sourceLocation));

    }
}
