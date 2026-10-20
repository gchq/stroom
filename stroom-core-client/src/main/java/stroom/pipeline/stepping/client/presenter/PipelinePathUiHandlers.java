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

package stroom.pipeline.stepping.client.presenter;

import stroom.widget.menu.client.presenter.Item;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;

import java.util.List;

/// The handlers of the stepping screen's pipeline bar ([PipelinePathPresenter]).
public interface PipelinePathUiHandlers {

    /// Shows one of the bar's drop down menus, e.g. the branches at a fork.
    ///
    /// @param items         The menu's items.
    /// @param popupPosition Where to show the menu.
    /// @param opener        The element that opened the menu, which clicking doesn't close it.
    /// @param focusItem     The item to focus when the menu is shown, e.g. the current branch, or
    ///                      null for the first.
    /// @param onHide        Called when the menu is hidden.
    void onShowMenu(List<Item> items,
                    PopupPosition popupPosition,
                    Element opener,
                    Item focusItem,
                    Runnable onHide);
}
