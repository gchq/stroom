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

package stroom.streamstore.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.streamstore.client.presenter.ClassificationWrapperPresenter;
import stroom.streamstore.client.presenter.ClassificationWrapperPresenter.ClassificationWrapperView;
import stroom.streamstore.client.presenter.DataPresenter;
import stroom.streamstore.client.presenter.DataPresenter.DataView;
import stroom.streamstore.client.presenter.EntityChoicePresenter;
import stroom.streamstore.client.presenter.EntityChoicePresenter.EntityChoiceView;
import stroom.streamstore.client.presenter.EntityIdSetPresenter;
import stroom.streamstore.client.presenter.EntityIdSetPresenter.EntityIdSetView;
import stroom.streamstore.client.presenter.IncludeExcludeEntityIdSetPopupPresenter;
import stroom.streamstore.client.presenter.IncludeExcludeEntityIdSetPopupPresenter.IncludeExcludeEntityIdSetPopupView;
import stroom.streamstore.client.presenter.IncludeExcludeEntityIdSetPresenter;
import stroom.streamstore.client.presenter.IncludeExcludeEntityIdSetPresenter.IncludeExcludeEntityIdSetView;
import stroom.streamstore.client.presenter.StreamAttributeListPresenter;
import stroom.streamstore.client.presenter.StreamAttributeListPresenter.StreamAttributeListView;
import stroom.streamstore.client.presenter.StreamAttributePresenter;
import stroom.streamstore.client.presenter.StreamAttributePresenter.StreamAttributeView;
import stroom.streamstore.client.presenter.StreamFilterPresenter;
import stroom.streamstore.client.presenter.StreamFilterPresenter.StreamFilterView;
import stroom.streamstore.client.presenter.StreamListPresenter;
import stroom.streamstore.client.presenter.StreamPresenter;
import stroom.streamstore.client.presenter.StreamPresenter.StreamView;
import stroom.streamstore.client.presenter.StreamTaskListPresenter;
import stroom.streamstore.client.presenter.StreamTaskPresenter;
import stroom.streamstore.client.presenter.StreamTaskPresenter.StreamTaskView;
import stroom.streamstore.client.presenter.StreamTypeUiManager;
import stroom.streamstore.client.presenter.TextPresenter;
import stroom.streamstore.client.presenter.TextPresenter.TextView;
import stroom.streamstore.client.view.ClassificationWrapperViewImpl;
import stroom.streamstore.client.view.DataViewImpl;
import stroom.streamstore.client.view.EntityChoiceViewImpl;
import stroom.streamstore.client.view.EntityIdSetViewImpl;
import stroom.streamstore.client.view.IncludeExcludeEntityIdSetPopupViewImpl;
import stroom.streamstore.client.view.IncludeExcludeEntityIdSetViewImpl;
import stroom.streamstore.client.view.StreamAttributeListViewImpl;
import stroom.streamstore.client.view.StreamAttributeViewImpl;
import stroom.streamstore.client.view.StreamFilterViewImpl;
import stroom.streamstore.client.view.StreamTaskViewImpl;
import stroom.streamstore.client.view.StreamViewImpl;
import stroom.streamstore.client.view.TextViewImpl;
import stroom.widget.dropdowntree.client.presenter.DropDownPresenter.DropDrownView;
import stroom.widget.dropdowntree.client.presenter.DropDownTreePresenter.DropDownTreeView;
import stroom.widget.dropdowntree.client.view.DropDownTreeViewImpl;
import stroom.widget.dropdowntree.client.view.DropDownViewImpl;

public class StreamStoreModule extends PluginModule {
    @Override
    protected void configure() {
        bind(StreamTypeUiManager.class).asEagerSingleton();

        bindPresenterWidget(ClassificationWrapperPresenter.class, ClassificationWrapperView.class,
                ClassificationWrapperViewImpl.class);
        bindPresenterWidget(StreamPresenter.class, StreamView.class, StreamViewImpl.class);
        bindPresenterWidget(DataPresenter.class, DataView.class, DataViewImpl.class);
        bindPresenterWidget(TextPresenter.class, TextView.class, TextViewImpl.class);
        bindPresenterWidget(StreamTaskPresenter.class, StreamTaskView.class, StreamTaskViewImpl.class);
        bindPresenterWidget(EntityIdSetPresenter.class, EntityIdSetView.class, EntityIdSetViewImpl.class);
        bindPresenterWidget(IncludeExcludeEntityIdSetPresenter.class, IncludeExcludeEntityIdSetView.class,
                IncludeExcludeEntityIdSetViewImpl.class);
        bindPresenterWidget(IncludeExcludeEntityIdSetPopupPresenter.class, IncludeExcludeEntityIdSetPopupView.class,
                IncludeExcludeEntityIdSetPopupViewImpl.class);
        bindPresenterWidget(EntityChoicePresenter.class, EntityChoiceView.class, EntityChoiceViewImpl.class);
        bindPresenterWidget(StreamFilterPresenter.class, StreamFilterView.class, StreamFilterViewImpl.class);
        bindPresenterWidget(StreamAttributeListPresenter.class, StreamAttributeListView.class,
                StreamAttributeListViewImpl.class);
        bindPresenterWidget(StreamAttributePresenter.class, StreamAttributeView.class, StreamAttributeViewImpl.class);
        bind(StreamListPresenter.class);

        bind(StreamTaskListPresenter.class);

        bindSharedView(DropDrownView.class, DropDownViewImpl.class);
        bindSharedView(DropDownTreeView.class, DropDownTreeViewImpl.class);
    }
}
