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

package stroom.pool.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.pool.client.PoolMonitoringPlugin;
import stroom.pool.client.presenter.PoolPresenter;
import stroom.pool.client.presenter.PoolPresenter.PoolView;
import stroom.pool.client.view.PoolViewImpl;

public class PoolModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(PoolMonitoringPlugin.class);
        bindPresenterWidget(PoolPresenter.class, PoolView.class, PoolViewImpl.class);
    }
}
