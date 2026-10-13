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

package stroom.gwt.workbench.client.app.gin;

import stroom.entity.client.presenter.CopyDocumentPresenter;
import stroom.entity.client.presenter.CopyDocumentPresenter.CopyDocumentProxy;
import stroom.entity.client.presenter.CopyDocumentPresenter.CopyDocumentView;
import stroom.entity.client.presenter.CreateDocumentPresenter;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentProxy;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentView;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.entity.client.presenter.InfoDocumentPresenter.InfoDocumentProxy;
import stroom.entity.client.presenter.InfoDocumentPresenter.InfoDocumentView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownEditPresenter.MarkdownEditView;
import stroom.entity.client.presenter.MarkdownPreviewPresenter;
import stroom.entity.client.presenter.MarkdownPreviewPresenter.MarkdownPreviewView;
import stroom.entity.client.presenter.MoveDocumentPresenter;
import stroom.entity.client.presenter.MoveDocumentPresenter.MoveDocumentProxy;
import stroom.entity.client.presenter.MoveDocumentPresenter.MoveDocumentView;
import stroom.entity.client.presenter.NameDocumentPresenter;
import stroom.entity.client.presenter.NameDocumentPresenter.NameDocumentProxy;
import stroom.entity.client.presenter.NameDocumentView;
import stroom.entity.client.view.CopyDocumentViewImpl;
import stroom.entity.client.view.CreateDocumentViewImpl;
import stroom.entity.client.view.InfoDocumentViewImpl;
import stroom.entity.client.view.MarkdownEditViewImpl;
import stroom.entity.client.view.MarkdownPreviewViewImpl;
import stroom.entity.client.view.MoveDocumentViewImpl;
import stroom.entity.client.view.NameDocumentViewImpl;

import com.google.inject.Provides;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `EntityModule`
/// (`stroom/entity/client/gin/EntityModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class EntityScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(CreateDocumentPresenter.class,
                CreateDocumentView.class,
                CreateDocumentViewImpl.class);
        bindPresenterWidget(CopyDocumentPresenter.class,
                CopyDocumentView.class,
                CopyDocumentViewImpl.class);
        bindPresenterWidget(MoveDocumentPresenter.class,
                MoveDocumentView.class,
                MoveDocumentViewImpl.class);
        bindPresenterWidget(InfoDocumentPresenter.class,
                InfoDocumentView.class,
                InfoDocumentViewImpl.class);
        bindPresenterWidget(MarkdownEditPresenter.class,
                MarkdownEditView.class,
                MarkdownEditViewImpl.class);
        bindPresenterWidget(MarkdownPreviewPresenter.class,
                MarkdownPreviewView.class,
                MarkdownPreviewViewImpl.class);
        bindSharedView(NameDocumentView.class,
                NameDocumentViewImpl.class);
        bind(NameDocumentPresenter.class);
    }

    /// @return No proxy, see the class description.
    @Provides
    CreateDocumentProxy provideCreateDocumentProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    CopyDocumentProxy provideCopyDocumentProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    MoveDocumentProxy provideMoveDocumentProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    InfoDocumentProxy provideInfoDocumentProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    NameDocumentProxy provideNameDocumentProxy() {
        return null;
    }
}

