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

import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.client.component.UserRefPopup;
import stroom.security.client.component.UserRefPopupController;
import stroom.security.client.component.UserRefSelectionController;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.gwt.core.client.GWT;
import com.google.inject.Provider;

/// Creates Stroom's selection box controllers as Stroom's GIN modules would, with the services of
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
    /// @return The chooser's controller.
    public static ExplorerPopupController explorerPopup(final ScreenHarness harness) {
        if (ginjector == null) {
            ginjector = GWT.create(ExplorerGinjector.class);
        }
        ExplorerGinModule.setCurrent(harness);
        try {
            return ginjector.getExplorerPopupController();
        } finally {
            ExplorerGinModule.setCurrent(null);
        }
    }

    /// Creates the factory that gives quick filters their recent-filters drop-down. Its Advanced
    /// Query dialog is not available in the workbench, as building it would need the whole
    /// expression editor.
    ///
    /// @param harness The story's harness.
    /// @return The factory.
    public static QuickFilterContextHandlerFactory quickFilterContextHandlerFactory(final ScreenHarness harness) {
        return new QuickFilterContextHandlerFactory(harness.getRestFactory(), () -> {
            throw new UnsupportedOperationException("Advanced Query is not available in the workbench");
        }, harness.getAppRoot());
    }

    /// Creates a document picker: Stroom's [DocSelectionController] with its explorer popup,
    /// attached to a `DropDownBox` ([DocSelectionController#getBox()]).
    ///
    /// @param harness The story's harness.
    /// @return The controller.
    public static DocSelectionController docSelectionBox(final ScreenHarness harness) {
        return docSelectionBox(harness, explorerPopup(harness));
    }

    /// Creates a document selection box with the given explorer popup, e.g. one with its own
    /// caption.
    ///
    /// @param harness The story's harness.
    /// @param popup   The popup, from [#explorerPopup(ScreenHarness)].
    /// @return The controller.
    public static DocSelectionController docSelectionBox(final ScreenHarness harness,
                                                         final ExplorerPopupController popup) {
        final DocSelectionController controller = new DocSelectionController(harness.getEventBus(),
                popup,
                harness.getRestFactory());
        controller.attach(new DropDownBox());
        return controller;
    }

    /// Creates a user picker: Stroom's [UserRefSelectionController] with its user popup, attached
    /// to a `DropDownBox` ([UserRefSelectionController#getBox()]).
    ///
    /// @param harness The story's harness.
    /// @return The controller.
    public static UserRefSelectionController userRefSelectionBox(final ScreenHarness harness) {
        final UserRefPopupController popup = new UserRefPopupController(harness.getEventBus(),
                new UserRefPopup(),
                harness.getRestFactory(),
                harness.getUiConfigCache(),
                quickFilterContextHandlerFactory(harness));
        return new UserRefSelectionController(popup).attach(new DropDownBox());
    }

    /// @param harness The story's harness.
    /// @return A provider of document pickers' controllers, each with its explorer popup, e.g. for a
    ///         term editor.
    public static Provider<DocSelectionController> docSelectionControllerProvider(final ScreenHarness harness) {
        return () -> new DocSelectionController(harness.getEventBus(),
                explorerPopup(harness),
                harness.getRestFactory());
    }

    /// @param harness The story's harness.
    /// @return A provider of user pickers' controllers, each with its user popup, e.g. for a term
    ///         editor.
    public static Provider<UserRefSelectionController> userRefSelectionControllerProvider(
            final ScreenHarness harness) {
        return () -> new UserRefSelectionController(new UserRefPopupController(harness.getEventBus(),
                new UserRefPopup(),
                harness.getRestFactory(),
                harness.getUiConfigCache(),
                quickFilterContextHandlerFactory(harness)));
    }
}
