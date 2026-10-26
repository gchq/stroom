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

import stroom.widget.presenter.client.gin.AbstractPresenterModule;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter.XSDBrowserView;
import stroom.widget.xsdbrowser.client.view.XSDBrowserViewImpl;
import stroom.xmlschema.client.presenter.XMLSchemaPresenter;

/// The presenter and view bindings of Stroom's `XMLSchemaModule`
/// (`stroom/xmlschema/client/gin/XMLSchemaModule.java`),
/// without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class XMLSchemaScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(XMLSchemaPresenter.class);
        bindPresenterWidget(XSDBrowserPresenter.class,
                XSDBrowserView.class,
                XSDBrowserViewImpl.class);
    }
}

