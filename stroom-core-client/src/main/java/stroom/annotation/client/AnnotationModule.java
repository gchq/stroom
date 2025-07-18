/*
 * Copyright 2018 Crown Copyright
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

package stroom.annotation.client;

import stroom.annotation.client.AddEventLinkPresenter.AddEventLinkView;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.annotation.client.AnnotationTagCreatePresenter.AnnotationTagCreateView;
import stroom.annotation.client.AnnotationTagEditPresenter.AnnotationTagEditView;
import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.annotation.client.ChangeStatusPresenter.ChangeStatusView;
import stroom.annotation.client.ChooserPresenter.ChooserView;
import stroom.annotation.client.CommentEditPresenter.CommentEditView;
import stroom.annotation.client.DurationPresenter.DurationView;
import stroom.annotation.client.LinkedEventPresenter.LinkedEventView;
import stroom.core.client.gin.PluginModule;

public class AnnotationModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(AnnotationCreatePlugin.class);
        bindPlugin(AnnotationStatusPlugin.class);
        bindPlugin(AnnotationLabelPlugin.class);
        bindPlugin(AnnotationCollectionPlugin.class);
        bind(AnnotationEditSupport.class).asEagerSingleton();
        bindPresenterWidget(AnnotationEditPresenter.class, AnnotationEditView.class, AnnotationEditViewImpl.class);
        bindPresenterWidget(AnnotationTagCreatePresenter.class,
                AnnotationTagCreateView.class,
                AnnotationTagCreateViewImpl.class);
        bindPresenterWidget(AnnotationTagEditPresenter.class,
                AnnotationTagEditView.class,
                AnnotationTagEditViewImpl.class);
        bindPresenterWidget(LinkedEventPresenter.class, LinkedEventView.class, LinkedEventViewImpl.class);
        bindPresenterWidget(AddEventLinkPresenter.class, AddEventLinkView.class, AddEventLinkViewImpl.class);
        bindPresenterWidget(ChangeStatusPresenter.class, ChangeStatusView.class, ChangeStatusViewImpl.class);
        bindPresenterWidget(ChangeAssignedToPresenter.class,
                ChangeAssignedToView.class,
                ChangeAssignedToViewImpl.class);
        bindPresenterWidget(DurationPresenter.class, DurationView.class, DurationViewImpl.class);
        bindPresenterWidget(CommentEditPresenter.class, CommentEditView.class, CommentEditViewImpl.class);

        bindSharedView(ChooserView.class, ChooserViewImpl.class);
        bind(ChooserPresenter.class);
        bind(MultiChooserPresenter.class);
    }
}
