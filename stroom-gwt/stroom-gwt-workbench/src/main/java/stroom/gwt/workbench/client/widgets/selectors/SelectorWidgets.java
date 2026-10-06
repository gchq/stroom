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


package stroom.gwt.workbench.client.widgets.selectors;

import stroom.data.grid.client.PagerViewImpl;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.client.presenter.ExplorerPopupPresenter;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.widget.dropdowntree.client.view.DropDownViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Provider;

/// Creates Stroom's selection box presenters as Stroom's GIN modules would, with the services of
/// a story's [ScreenHarness], for the `Widgets/Selectors/*` stories and the widgets that contain
/// them (e.g. the expression builder's term editors).
public final class SelectorWidgets {

    private static ExplorerGinjector ginjector;

    private SelectorWidgets() {
        // Static utility
    }

    /// Creates an explorer popup (the tree dialog a document selection box opens).
    ///
    /// @param harness The story's harness.
    /// @return The presenter.
    public static ExplorerPopupPresenter explorerPopup(final ScreenHarness harness) {
        if (ginjector == null) {
            ginjector = GWT.create(ExplorerGinjector.class);
        }
        ExplorerGinModule.setCurrent(harness);
        try {
            return ginjector.getExplorerPopupPresenter();
        } finally {
            ExplorerGinModule.setCurrent(null);
        }
    }

    /// Creates a document selection box: Stroom's [DocSelectionBoxPresenter] with its
    /// `DropDownViewImpl` and explorer popup.
    ///
    /// @param harness The story's harness.
    /// @return The presenter.
    public static DocSelectionBoxPresenter docSelectionBox(final ScreenHarness harness) {
        return docSelectionBox(harness, explorerPopup(harness));
    }

    /// Creates a document selection box with the given explorer popup, e.g. one with its own
    /// caption.
    ///
    /// @param harness The story's harness.
    /// @param popup   The popup, from [#explorerPopup(ScreenHarness)].
    /// @return The presenter.
    public static DocSelectionBoxPresenter docSelectionBox(final ScreenHarness harness,
                                                           final ExplorerPopupPresenter popup) {
        return new DocSelectionBoxPresenter(harness.getEventBus(),
                new DropDownViewImpl(GWT.create(DropDownViewImpl.Binder.class)),
                popup,
                harness.getRestFactory());
    }

    /// Creates a user selection box: Stroom's [UserRefSelectionBoxPresenter] with its
    /// `DropDownViewImpl` and user popup.
    ///
    /// @param harness The story's harness.
    /// @return The presenter.
    public static UserRefSelectionBoxPresenter userRefSelectionBox(final ScreenHarness harness) {
        final UserRefPopupPresenter popup = new UserRefPopupPresenter(harness.getEventBus(),
                new QuickFilterDialogViewImpl(GWT.create(QuickFilterDialogViewImpl.Binder.class)),
                new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class)),
                harness.getRestFactory(),
                harness.getUiConfigCache());
        return new UserRefSelectionBoxPresenter(harness.getEventBus(),
                new DropDownViewImpl(GWT.create(DropDownViewImpl.Binder.class)),
                popup);
    }

    /// @param harness The story's harness.
    /// @return A provider of document selection boxes, e.g. for the expression builder.
    public static Provider<DocSelectionBoxPresenter> docSelectionBoxProvider(final ScreenHarness harness) {
        return () -> docSelectionBox(harness);
    }

    /// @param harness The story's harness.
    /// @return A provider of user selection boxes, e.g. for the expression builder.
    public static Provider<UserRefSelectionBoxPresenter> userRefSelectionBoxProvider(final ScreenHarness harness) {
        return () -> userRefSelectionBox(harness);
    }
}
