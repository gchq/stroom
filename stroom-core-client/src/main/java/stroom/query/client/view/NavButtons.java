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

import stroom.query.client.presenter.NavUiHandlers;
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

public class NavButtons extends Composite implements HasUiHandlers<NavUiHandlers> {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    InlineSvgButton backward;
    @UiField
    InlineSvgButton forward;

    private NavUiHandlers uiHandlers;

    public NavButtons() {
        initWidget(BINDER.createAndBindUi(this));
        backward.setSvg(SvgImage.BACKWARD);
        forward.setSvg(SvgImage.FORWARD);
    }

    @Override
    public void setUiHandlers(final NavUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    @UiHandler("backward")
    public void onBackwardClick(final ClickEvent event) {
        if (uiHandlers != null) {
            uiHandlers.backward();
        }
    }

    @UiHandler("forward")
    public void onForwardClick(final ClickEvent event) {
        if (uiHandlers != null) {
            uiHandlers.forward();
        }
    }

    public interface Binder extends UiBinder<Widget, NavButtons> {

    }
}
