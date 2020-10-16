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

package stroom.dashboard.client.gin;

import stroom.core.client.UrlParameters;
import stroom.core.client.gin.InactivePlaceManager;
import stroom.core.client.presenter.CorePresenter;
import stroom.core.client.presenter.CorePresenter.CoreProxy;
import stroom.core.client.presenter.CorePresenter.CoreView;
import stroom.core.client.view.CoreViewImpl;
import stroom.dashboard.client.main.DashboardMainPresenter;
import stroom.dashboard.client.main.DashboardMainPresenter.DashboardMainProxy;
import stroom.dashboard.client.main.DashboardMainPresenter.DashboardMainView;
import stroom.dashboard.client.main.DashboardMainViewImpl;
import stroom.data.client.presenter.CharacterNavigatorPresenter;
import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
import stroom.data.client.presenter.CharacterRangeSelectionPresenter;
import stroom.data.client.presenter.CharacterRangeSelectionPresenter.CharacterRangeSelectionView;
import stroom.data.client.presenter.ClassificationWrapperPresenter;
import stroom.data.client.presenter.ClassificationWrapperPresenter.ClassificationWrapperView;
import stroom.data.client.presenter.DataPresenter;
import stroom.data.client.presenter.DataPresenter.DataView;
import stroom.data.client.presenter.ItemNavigatorPresenter;
import stroom.data.client.presenter.ItemNavigatorPresenter.ItemNavigatorView;
import stroom.data.client.presenter.ItemSelectionPresenter;
import stroom.data.client.presenter.ItemSelectionPresenter.ItemSelectionView;
import stroom.data.client.presenter.SourcePresenter;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.data.client.presenter.TextPresenter;
import stroom.data.client.presenter.TextPresenter.TextView;
import stroom.data.client.view.CharacterNavigatorViewImpl;
import stroom.data.client.view.CharacterRangeSelectionViewImpl;
import stroom.data.client.view.ClassificationWrapperViewImpl;
import stroom.data.client.view.DataViewImpl;
import stroom.data.client.view.ItemNavigatorViewImpl;
import stroom.data.client.view.ItemSelectionViewImpl;
import stroom.data.client.view.SourceTabViewImpl;
import stroom.data.client.view.SourceViewImpl;
import stroom.data.client.view.TextViewImpl;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.editor.client.view.EditorViewImpl;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.client.view.EntityTreeViewImpl;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.iframe.client.presenter.IFramePresenter.IFrameView;
import stroom.iframe.client.view.IFrameContentViewImpl;
import stroom.iframe.client.view.IFrameViewImpl;
import stroom.widget.dropdowntree.client.presenter.DropDownPresenter;
import stroom.widget.dropdowntree.client.presenter.DropDownTreePresenter;
import stroom.widget.dropdowntree.client.view.DropDownTreeViewImpl;
import stroom.widget.dropdowntree.client.view.DropDownViewImpl;

import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.RootPresenter;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;
import com.gwtplatform.mvp.client.proxy.ParameterTokenFormatter;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.TokenFormatter;

public class DashboardAppModule extends AbstractPresenterModule {
    @Override
    protected void configure() {
        // Default implementation of standard resources
        bind(EventBus.class).to(SimpleEventBus.class).in(Singleton.class);
        bind(TokenFormatter.class).to(ParameterTokenFormatter.class).in(Singleton.class);
        bind(RootPresenter.class).asEagerSingleton();
        bind(PlaceManager.class).to(InactivePlaceManager.class).in(Singleton.class);
        bind(UrlParameters.class).in(Singleton.class);

        // Presenters
        bindPresenter(CorePresenter.class, CoreView.class, CoreViewImpl.class, CoreProxy.class);

        bindPresenter(DashboardMainPresenter.class, DashboardMainView.class, DashboardMainViewImpl.class, DashboardMainProxy.class);

        bindSharedView(DropDownPresenter.DropDrownView.class, DropDownViewImpl.class);
        bindSharedView(DropDownTreePresenter.DropDownTreeView.class, DropDownTreeViewImpl.class);
        bindPresenterWidget(EntityTreePresenter.class, EntityTreePresenter.EntityTreeView.class, EntityTreeViewImpl.class);
        bindPresenterWidget(EditorPresenter.class, EditorView.class, EditorViewImpl.class);
        bindPresenterWidget(IFramePresenter.class, IFrameView.class, IFrameViewImpl.class);
        bindPresenterWidget(IFrameContentPresenter.class, IFrameContentView.class, IFrameContentViewImpl.class);

        bindPresenterWidget(ClassificationWrapperPresenter.class, ClassificationWrapperView.class, ClassificationWrapperViewImpl.class);
        bindPresenterWidget(DataPresenter.class, DataView.class, DataViewImpl.class);
        bindPresenterWidget(TextPresenter.class, TextView.class, TextViewImpl.class);

        bindPresenterWidget(
                SourceTabPresenter.class,
                SourceTabView.class,
                SourceTabViewImpl.class);
        bindPresenterWidget(
                SourcePresenter.class,
                SourceView.class,
                SourceViewImpl.class);
        bindPresenterWidget(
                CharacterRangeSelectionPresenter.class,
                CharacterRangeSelectionView.class,
                CharacterRangeSelectionViewImpl.class);
        bindPresenterWidget(
                CharacterNavigatorPresenter.class,
                CharacterNavigatorView.class,
                CharacterNavigatorViewImpl.class);
        bindPresenterWidget(
                ItemNavigatorPresenter.class,
                ItemNavigatorView.class,
                ItemNavigatorViewImpl.class);
        bindPresenterWidget(
                ItemSelectionPresenter.class,
                ItemSelectionView.class,
                ItemSelectionViewImpl.class);
    }
}
