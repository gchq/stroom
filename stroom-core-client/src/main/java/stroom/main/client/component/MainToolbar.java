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

package stroom.main.client.component;

import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The main toolbar: the busy spinner, the Ask Stroom AI toggle and the main menu's button. Its markup
/// is `MainToolbar.ui.xml`.
public class MainToolbar extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TaskSpinner spinner;
    @UiField
    ToolbarButton menu;
    @UiField
    ToolbarToggleButton showAi;

    private Consumer<Element> onShowMenu;
    private Runnable onShowAi;

    public MainToolbar() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The spinner shown while the app is busy, for its task monitor.
    public TaskSpinner getSpinner() {
        return spinner;
    }

    /// @return The button that shows and hides Ask Stroom AI, whose state its owner keeps.
    public ToolbarToggleButton getShowAi() {
        return showAi;
    }

    /// @param onShowMenu Told the main menu's button when it is pressed, to show the menu below it,
    ///                   or null for nothing.
    public void setOnShowMenu(final Consumer<Element> onShowMenu) {
        this.onShowMenu = onShowMenu;
    }

    /// @param onShowAi Run when the Ask Stroom AI toggle is pressed, or null for nothing.
    public void setOnShowAi(final Runnable onShowAi) {
        this.onShowAi = onShowAi;
    }

    @UiHandler("menu")
    void onMenu(final ClickEvent event) {
        if (MouseUtil.isPrimary(event) && onShowMenu != null) {
            onShowMenu.accept(menu.getElement());
        }
    }

    @UiHandler("showAi")
    void onShowAi(final ClickEvent event) {
        if (MouseUtil.isPrimary(event) && onShowAi != null) {
            onShowAi.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MainToolbar> {

    }
}
