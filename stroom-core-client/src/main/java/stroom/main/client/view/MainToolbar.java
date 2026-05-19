/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.main.client.view;

import stroom.main.client.presenter.MainUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class MainToolbar extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Spinner spinner;
    @UiField
    InlineSvgButton menu;
    @UiField
    InlineSvgToggleButton showAi;

    private MainUiHandlers mainUiHandlers;

    public MainToolbar() {
        initWidget(BINDER.createAndBindUi(this));
        menu.setSvg(SvgImage.ELLIPSES_VERTICAL);
        showAi.setSvg(SvgImage.AI);
    }

    public Spinner getSpinner() {
        return spinner;
    }

    public InlineSvgToggleButton getShowAi() {
        return showAi;
    }

    public void setMainUiHandlers(final MainUiHandlers mainUiHandlers) {
        this.mainUiHandlers = mainUiHandlers;
    }

    @UiHandler("menu")
    void onMenu(final ClickEvent event) {
        if (MouseUtil.isPrimary(event) && mainUiHandlers != null) {
            mainUiHandlers.showMenu(event.getNativeEvent(), menu.getElement());
        }
    }

    @UiHandler("showAi")
    void onShowAi(final ClickEvent event) {
        if (MouseUtil.isPrimary(event) && mainUiHandlers != null) {
            mainUiHandlers.showAi(event.getNativeEvent(), menu.getElement());
        }
    }

    public interface Binder extends UiBinder<Widget, MainToolbar> {

    }
}
