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

package stroom.receive.content.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.receive.content.client.presenter.ContentTemplateEditPresenter;
import stroom.receive.content.client.presenter.ContentTemplateEditPresenter.ContentTemplateEditView;
import stroom.receive.content.client.presenter.ContentTemplateTabPresenter;
import stroom.receive.content.client.presenter.ContentTemplateTabPresenter.ContentTemplateTabView;
import stroom.receive.content.client.view.ContentTemplateEditViewImpl;
import stroom.receive.content.client.view.ContentTemplateTabViewImpl;
import stroom.receive.rules.client.ContentTemplatePlugin;

public class ContentTemplateModule extends PluginModule {

    @Override
    protected void configure() {

        bindPlugin(ContentTemplatePlugin.class);
        bindPresenterWidget(ContentTemplateTabPresenter.class,
                ContentTemplateTabView.class,
                ContentTemplateTabViewImpl.class);
        bindPresenterWidget(ContentTemplateEditPresenter.class,
                ContentTemplateEditView.class,
                ContentTemplateEditViewImpl.class);
    }
}
