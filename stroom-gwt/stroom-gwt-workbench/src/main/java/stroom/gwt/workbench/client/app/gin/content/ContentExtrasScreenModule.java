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

import stroom.alert.client.presenter.CommonAlertPresenter;
import stroom.alert.client.presenter.CommonAlertPresenter.CommonAlertView;
import stroom.alert.client.view.CommonAlertViewImpl;
import stroom.data.client.presenter.DataUploadPresenter;
import stroom.data.client.presenter.DataUploadPresenter.DataUploadView;
import stroom.data.client.view.DataUploadViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// Single bindings from Stroom's modules that the content screens need (the data browser, through
/// the annotation editor's linked events) without the rest of their module: `AlertModule`'s alert
/// dialog (the data browser's selection summary shows one) and `FeedModule`'s data upload dialog
/// (opened from the data browser).
public class ContentExtrasScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(CommonAlertPresenter.class,
                CommonAlertView.class,
                CommonAlertViewImpl.class);
        bindPresenterWidget(DataUploadPresenter.class,
                DataUploadView.class,
                DataUploadViewImpl.class);
    }
}
