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

package stroom.http.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.http.client.presenter.HttpClientConfigPresenter;
import stroom.http.client.presenter.HttpClientConfigPresenter.HttpClientConfigView;
import stroom.http.client.presenter.HttpTlsConfigPresenter;
import stroom.http.client.presenter.HttpTlsConfigPresenter.HttpTlsConfigView;
import stroom.http.client.view.HttpClientConfigViewImpl;
import stroom.http.client.view.HttpTlsConfigViewImpl;

public class HttpModule extends PluginModule {

    @Override
    protected void configure() {
        bindPresenterWidget(HttpClientConfigPresenter.class,
                HttpClientConfigView.class,
                HttpClientConfigViewImpl.class);
        bindPresenterWidget(HttpTlsConfigPresenter.class,
                HttpTlsConfigView.class,
                HttpTlsConfigViewImpl.class);
    }
}
