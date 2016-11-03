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

package stroom.entity.client.gin;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

import stroom.entity.client.presenter.CopyEntityPresenter;
import stroom.entity.client.presenter.CopyEntityPresenter.CopyEntityProxy;
import stroom.entity.client.presenter.CopyEntityPresenter.CopyEntityView;
import stroom.entity.client.presenter.CreateEntityPresenter;
import stroom.entity.client.presenter.CreateEntityPresenter.CreateEntityProxy;
import stroom.entity.client.presenter.CreateEntityPresenter.CreateEntityView;
import stroom.entity.client.presenter.MoveEntityPresenter;
import stroom.entity.client.presenter.MoveEntityPresenter.MoveEntityProxy;
import stroom.entity.client.presenter.MoveEntityPresenter.MoveEntityView;
import stroom.entity.client.presenter.NameEntityPresenter;
import stroom.entity.client.presenter.NameEntityPresenter.RenameEntityProxy;
import stroom.entity.client.presenter.NameEntityView;
import stroom.entity.client.presenter.SaveAsEntityPresenter;
import stroom.entity.client.presenter.SaveAsEntityPresenter.SaveAsEntityProxy;
import stroom.entity.client.presenter.SaveAsEntityPresenter.SaveAsEntityView;
import stroom.entity.client.view.CopyEntityViewImpl;
import stroom.entity.client.view.CreateEntityViewImpl;
import stroom.entity.client.view.MoveEntityViewImpl;
import stroom.entity.client.view.NameEntityViewImpl;
import stroom.entity.client.view.SaveAsEntityViewImpl;

public class EntityModule extends AbstractPresenterModule {
    @Override
    protected void configure() {
        bindPresenter(CreateEntityPresenter.class, CreateEntityView.class, CreateEntityViewImpl.class,
                CreateEntityProxy.class);
        bindPresenter(CopyEntityPresenter.class, CopyEntityView.class, CopyEntityViewImpl.class, CopyEntityProxy.class);
        bindPresenter(MoveEntityPresenter.class, MoveEntityView.class, MoveEntityViewImpl.class, MoveEntityProxy.class);
        bindSharedView(NameEntityView.class, NameEntityViewImpl.class);
        bindPresenter(NameEntityPresenter.class, RenameEntityProxy.class);
        bindPresenter(SaveAsEntityPresenter.class, SaveAsEntityView.class, SaveAsEntityViewImpl.class,
                SaveAsEntityProxy.class);
    }
}
