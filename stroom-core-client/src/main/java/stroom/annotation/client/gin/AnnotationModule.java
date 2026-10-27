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

package stroom.annotation.client.gin;

import stroom.annotation.client.AnnotationBrowsePlugin;
import stroom.annotation.client.AnnotationCollectionPlugin;
import stroom.annotation.client.AnnotationCommentPlugin;
import stroom.annotation.client.AnnotationCreatePlugin;
import stroom.annotation.client.AnnotationEditSupport;
import stroom.annotation.client.AnnotationLabelPlugin;
import stroom.annotation.client.AnnotationStatusPlugin;
import stroom.annotation.client.LinkedEventPresenter;
import stroom.annotation.client.LinkedEventPresenter.LinkedEventView;
import stroom.annotation.client.LinkedEventViewImpl;
import stroom.core.client.gin.PluginModule;
import stroom.explorer.client.component.FindPanel;
import stroom.explorer.client.presenter.FindView;

public class AnnotationModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(AnnotationBrowsePlugin.class);
        bindPlugin(AnnotationCreatePlugin.class);
        bindPlugin(AnnotationStatusPlugin.class);
        bindPlugin(AnnotationLabelPlugin.class);
        bindPlugin(AnnotationCollectionPlugin.class);
        bindPlugin(AnnotationCommentPlugin.class);
        bind(AnnotationEditSupport.class).asEagerSingleton();
        bindPresenterWidget(LinkedEventPresenter.class, LinkedEventView.class, LinkedEventViewImpl.class);
        bindSharedView(FindView.class, FindPanel.class);
    }
}
