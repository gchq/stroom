/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.content;

import stroom.data.client.presenter.CharacterNavigatorPresenter;
import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
import stroom.data.client.presenter.CharacterRangeSelectionPresenter;
import stroom.data.client.presenter.CharacterRangeSelectionPresenter.CharacterRangeSelectionView;
import stroom.data.client.presenter.ClassificationWrapperView;
import stroom.data.client.presenter.DataPresenter;
import stroom.data.client.presenter.DataPresenter.DataView;
import stroom.data.client.presenter.DataPreviewTabPresenter;
import stroom.data.client.presenter.DataPreviewTabPresenter.DataPreviewTabView;
import stroom.data.client.presenter.ItemNavigatorPresenter;
import stroom.data.client.presenter.ItemNavigatorPresenter.ItemNavigatorView;
import stroom.data.client.presenter.ItemSelectionPresenter;
import stroom.data.client.presenter.ItemSelectionPresenter.ItemSelectionView;
import stroom.data.client.presenter.MetaListPresenter;
import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.MetaPresenter.MetaView;
import stroom.data.client.presenter.ProcessChoicePresenter;
import stroom.data.client.presenter.ProcessChoicePresenter.ProcessChoiceView;
import stroom.data.client.presenter.ProcessorTaskListPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter.StreamTaskView;
import stroom.data.client.presenter.SourcePresenter;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.data.client.presenter.SteppingMetaListPresenter;
import stroom.data.client.presenter.TextPresenter;
import stroom.data.client.presenter.TextPresenter.TextView;
import stroom.data.client.view.CharacterNavigatorViewImpl;
import stroom.data.client.view.CharacterRangeSelectionViewImpl;
import stroom.data.client.view.ClassificationWrapperViewImpl;
import stroom.data.client.view.DataPreviewTabViewImpl;
import stroom.data.client.view.DataViewImpl;
import stroom.data.client.view.ItemNavigatorViewImpl;
import stroom.data.client.view.ItemSelectionViewImpl;
import stroom.data.client.view.MetaViewImpl;
import stroom.data.client.view.ProcessChoiceViewImpl;
import stroom.data.client.view.SourceTabViewImpl;
import stroom.data.client.view.SourceViewImpl;
import stroom.data.client.view.StreamTaskViewImpl;
import stroom.data.client.view.TextViewImpl;
import stroom.widget.progress.client.presenter.ProgressPresenter;
import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;
import stroom.widget.progress.client.view.ProgressViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `StreamStoreModule`
/// (`stroom/data/client/gin/StreamStoreModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class StreamStoreScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(MetaPresenter.class,
                MetaView.class,
                MetaViewImpl.class);
        bindPresenterWidget(DataPreviewTabPresenter.class,
                DataPreviewTabView.class,
                DataPreviewTabViewImpl.class);
        bindPresenterWidget(DataPresenter.class,
                DataView.class,
                DataViewImpl.class);
        bindPresenterWidget(TextPresenter.class,
                TextView.class,
                TextViewImpl.class);
        bindPresenterWidget(ProcessorTaskPresenter.class,
                StreamTaskView.class,
                StreamTaskViewImpl.class);
        bindPresenterWidget(ProcessChoicePresenter.class,
                ProcessChoiceView.class,
                ProcessChoiceViewImpl.class);
        bindPresenterWidget(SourceTabPresenter.class,
                SourceTabView.class,
                SourceTabViewImpl.class);
        bindPresenterWidget(SourcePresenter.class,
                SourceView.class,
                SourceViewImpl.class);
        bindPresenterWidget(CharacterRangeSelectionPresenter.class,
                CharacterRangeSelectionView.class,
                CharacterRangeSelectionViewImpl.class);
        bindPresenterWidget(CharacterNavigatorPresenter.class,
                CharacterNavigatorView.class,
                CharacterNavigatorViewImpl.class);
        bindPresenterWidget(ItemNavigatorPresenter.class,
                ItemNavigatorView.class,
                ItemNavigatorViewImpl.class);
        bindPresenterWidget(ItemSelectionPresenter.class,
                ItemSelectionView.class,
                ItemSelectionViewImpl.class);
        bindPresenterWidget(ProgressPresenter.class,
                ProgressView.class,
                ProgressViewImpl.class);
        bind(MetaListPresenter.class);
        bind(SteppingMetaListPresenter.class);
        bind(ProcessorTaskListPresenter.class);
        bindSharedView(ClassificationWrapperView.class,
                ClassificationWrapperViewImpl.class);
    }
}

