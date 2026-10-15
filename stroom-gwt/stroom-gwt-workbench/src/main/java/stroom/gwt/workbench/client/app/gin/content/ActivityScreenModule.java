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

import stroom.activity.client.ActivityEditPresenter;
import stroom.activity.client.ActivityEditPresenter.ActivityEditView;
import stroom.activity.client.ActivityEditViewImpl;
import stroom.activity.client.ManageActivityPresenter;
import stroom.activity.client.ManageActivityPresenter.ManageActivityView;
import stroom.activity.client.ManageActivityViewImpl;
import stroom.activity.client.SplashPresenter;
import stroom.activity.client.SplashPresenter.SplashView;
import stroom.activity.client.SplashViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `ActivityModule`
/// (`stroom/activity/client/ActivityModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class ActivityScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(SplashPresenter.class,
                SplashView.class,
                SplashViewImpl.class);
        bindPresenterWidget(ManageActivityPresenter.class,
                ManageActivityView.class,
                ManageActivityViewImpl.class);
        bindPresenterWidget(ActivityEditPresenter.class,
                ActivityEditView.class,
                ActivityEditViewImpl.class);
    }
}

