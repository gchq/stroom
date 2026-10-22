/*
 * Copyright 2021 Crown Copyright
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

package stroom.widget.dropdowntree.client.view;

import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Supplier;

public interface ExplorerPopupView extends View, Focus, HasUiHandlers<ExplorerPopupUiHandlers> {

    /**
     * Show why the server rejected the filter, or clear it when passed null.
     * See {@code FetchExplorerNodeResult.filterError}.
     */
    void setFilterError(String filterError);

    void setQuickFilterContextHandler(QuickFilterContextHandler contextHandler);

    /// Names the quick filter's text box for screen readers, by what it filters (e.g. "Filter
    /// users"), as nothing on screen names it.
    ///
    /// @param accessibleName The name.
    void setAccessibleName(String accessibleName);

    void setCellTree(Widget widget);

    void setQuickFilterTooltipSupplier(final Supplier<SafeHtml> tooltipSupplier);

    void clearQuickFilter();

    void setQuickFilter(final String filterInput);

    /// Sets what Down in the filter does, e.g. moves the focus into the list below it.
    ///
    /// @param moveDown What to do.
    void setFilterMoveDown(Runnable moveDown);

    /// Types characters in the filter (moving the focus to it), e.g. those typed in the list.
    ///
    /// @param text The characters.
    void typeInFilter(String text);

    /// Says something to screen readers (a polite live region), e.g. the item now chosen.
    ///
    /// @param message What to say.
    void announce(String message);
}
