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

package stroom.query.client.view;

import stroom.query.client.presenter.QueryUiHandlers;
import stroom.query.client.presenter.SearchStateListener;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.HasUiHandlers;

public class QueryButtons
        extends Composite
        implements HasUiHandlers<QueryUiHandlers>, SearchStateListener {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    InlineSvgButton start;

    private QueryUiHandlers uiHandlers;

    public QueryButtons() {
        initWidget(BINDER.createAndBindUi(this));
        start.setSvg(SvgImage.PLAY);
    }

    @Override
    public void setUiHandlers(final QueryUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    @UiHandler("start")
    public void onStartClick(final ClickEvent event) {
        if (uiHandlers != null) {
            uiHandlers.start();
        }
    }

    @Override
    public void onSearching(final boolean searching) {
        if (searching) {
            start.addStyleName("QueryButtons-button stop");
            start.removeStyleName("QueryButtons-button play");
            start.setSvg(SvgImage.STOP);
            start.setTitle("Stop Query");
        } else {
            start.addStyleName("QueryButtons-button play");
            start.removeStyleName("QueryButtons-button stop");
            start.setSvg(SvgImage.PLAY);
            start.setTitle("Execute Query");
        }
    }

    public void setEnabled(final boolean enabled) {
        start.setEnabled(enabled);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, QueryButtons> {

    }
}
