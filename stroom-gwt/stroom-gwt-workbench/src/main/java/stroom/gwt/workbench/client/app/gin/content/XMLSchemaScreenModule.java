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

import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter.XSDBrowserView;
import stroom.widget.xsdbrowser.client.view.XSDBrowserViewImpl;
import stroom.xmlschema.client.presenter.XMLSchemaPresenter;
import stroom.xmlschema.client.presenter.XMLSchemaSettingsPresenter;
import stroom.xmlschema.client.presenter.XMLSchemaSettingsPresenter.XMLSchemaSettingsView;
import stroom.xmlschema.client.view.XMLSchemaSettingsViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `XMLSchemaModule`
/// (`stroom/xmlschema/client/gin/XMLSchemaModule.java`),
/// without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class XMLSchemaScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(XMLSchemaPresenter.class);
        bindPresenterWidget(XMLSchemaSettingsPresenter.class,
                XMLSchemaSettingsView.class,
                XMLSchemaSettingsViewImpl.class);
        bindPresenterWidget(XSDBrowserPresenter.class,
                XSDBrowserView.class,
                XSDBrowserViewImpl.class);
    }
}

