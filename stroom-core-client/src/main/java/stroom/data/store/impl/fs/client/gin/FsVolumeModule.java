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

package stroom.data.store.impl.fs.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.data.store.impl.fs.client.presenter.FsVolumeEditPresenter;
import stroom.data.store.impl.fs.client.presenter.FsVolumeEditPresenter.FsVolumeEditView;
import stroom.data.store.impl.fs.client.presenter.FsVolumeGroupEditPresenter;
import stroom.data.store.impl.fs.client.presenter.FsVolumeGroupEditPresenter.FsVolumeGroupEditView;
import stroom.data.store.impl.fs.client.view.FsVolumeEditViewImpl;
import stroom.data.store.impl.fs.client.view.FsVolumeGroupEditViewImpl;

public class FsVolumeModule extends PluginModule {

    @Override
    protected void configure() {
        bindPresenterWidget(
                FsVolumeEditPresenter.class,
                FsVolumeEditView.class,
                FsVolumeEditViewImpl.class);

        bindPresenterWidget(
                FsVolumeGroupEditPresenter.class,
                FsVolumeGroupEditView.class,
                FsVolumeGroupEditViewImpl.class);
    }
}
