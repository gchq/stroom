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


package stroom.gwt.workbench.client.widgets.dialogs;

import stroom.explorer.client.presenter.ExplorerPopupPresenter;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.selectors.SelectorWidgets;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [ExplorerPopupPresenter] (the explorer tree dialog that a document
/// selection box opens, with a quick filter above the tree), matching
/// `Widgets/Dialogs/ExplorerDropdownPopup` in the React Storybook.
///
/// The presenter fetches its tree from the explorer service, which the harness answers from the
/// React stories' `FIXTURE_TREE` (`TreeFixtures.fixtureTree()`); it is created with
/// `SelectorWidgets.explorerPopup`, as its constructor is only visible to GIN.
public final class ExplorerDropdownPopupStories {

    // The React ExplorerDropdownPopup's callback prop
    private static final String ON_CLOSE = "onClose";

    private ExplorerDropdownPopupStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's defaultOpenItems has no GWT equivalent: Stroom's explorer tree opens the
        // folders the explorer service says are open
        registry.component("Widgets/Dialogs/ExplorerDropdownPopup", ExplorerDropdownPopupStories.class)
                .layout(StoryLayout.CENTERED)
                // Explorer dropdown popup, open from the start; type in the filter to narrow the tree
                .story("Default", "Explorer dropdown popup", context -> render(context, "Select document…"))
                // With included types — only Feed and Pipeline documents (and folders) are shown
                .story("TypeFiltered", "Explorer dropdown popup — included types",
                        context -> render(context, "Select feed / pipeline…",
                        "Folder", "Feed", "Pipeline"));
    }

    private static Widget render(final StoryContext context, final String buttonText, final String... includedTypes) {
        final Spy onClose = context.fn(ON_CLOSE);
        final ScreenHarness harness = ScreenHarness.create(context,
                TreeFixtures.explorerRoutes(TreeFixtures.fixtureTree()).build());
        final ExplorerPopupPresenter popup = SelectorWidgets.explorerPopup(harness);
        // Differs from React: the tree starts with Stroom's "None" row (the presenter's
        // default setIncludeNullSelection(true))
        if (includedTypes.length > 0) {
            popup.setIncludedTypes(includedTypes);
        }
        harness.addRegistration(harness.getEventBus().addHandler(HidePopupEvent.getType(), event -> {
            if (event.getPresenterWidget() == popup) {
                onClose.call();
            }
        }));
        harness.add(DialogWidgets.button(buttonText, DialogWidgets.PRIMARY, event -> show(popup)));
        // React's `useState(true)`: the popup is shown from the start
        show(popup);
        return harness.asWidget();
    }

    private static void show(final ExplorerPopupPresenter popup) {
        popup.show(docRef -> {
            // The React story only closes the popup
        });
    }
}
