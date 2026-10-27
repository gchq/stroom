/*
 * Copyright 2023 Crown Copyright
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

package stroom.explorer.client.presenter;

import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;

import java.util.function.Supplier;

/// A filter above a list of what it finds, as the annotation presenters not yet split see the
/// shared `FindPanel`: Find Annotation, Annotation Links and Browse Annotations. It goes once they
/// are components.
public interface FindView extends View, Focus, HasUiHandlers<FindUiHandlers> {

    /// Shows why the server rejected the filter, or clears it when passed null. See
    /// `ResultPage.filterError`.
    ///
    /// @param filterError Why, or null.
    void setFilterError(String filterError);

    /// @param contextHandler Gives the filter its recent filters.
    void setQuickFilterContextHandler(QuickFilterContextHandler contextHandler);

    /// @param view The list of what is found.
    void setResultView(IsWidget view);

    /// @param dialog Whether it is a dialog's content, whose top then has no padding of its own.
    void setDialogMode(boolean dialog);

    /// Says what the filter is for and what may be typed in it, for screen readers.
    ///
    /// @param accessibleName What the filter is for, e.g. `Find by name`.
    /// @param description    What may be typed, or null for nothing.
    void setFilterGuidance(String accessibleName, String description);

    /// Sets the help shown by the filter's syntax help button.
    ///
    /// @param popupTextSupplier Supplies the help.
    void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);
}
