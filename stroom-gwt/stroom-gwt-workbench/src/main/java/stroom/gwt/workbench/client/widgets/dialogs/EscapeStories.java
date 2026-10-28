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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.StoryContent;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.form.client.TextBox;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Escape in a dialog: the widget with the focus has it first, and the dialog closes
/// only if no widget used it. The page behind never sees it.
public final class EscapeStories {

    // Spy names
    private static final String ON_OK = "onOk";
    private static final String ON_CANCEL = "onCancel";
    private static final String PAGE_ESCAPE = "pageEscape";

    private static final String OPEN = "Open dialog";
    private static final String CAPTION = "Escape test";
    private static final String FILTER_NAME = "Filter items";
    private static final String NAME = "Name";

    private EscapeStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Dialogs/Escape", EscapeStories.class)
                .layout(StoryLayout.CENTERED)
                // Escape in a quick filter with text clears it and leaves the dialog open; Escape in
                // the empty filter cancels the dialog, and the focus goes back to the opener
                .story("WidgetFirst", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    return DialogWidgets.button(OPEN, DialogWidgets.PRIMARY, event -> {
                        final QuickFilter quickFilter = new QuickFilter();
                        quickFilter.setAccessibleName(FILTER_NAME);
                        show(spies, popups, quickFilter, quickFilter::focus);
                    });
                })
                .withPlay(play -> {
                    final Query opener = play.getByRole("button", StroomDom.button(OPEN));
                    play.click(opener);
                    final Query filter = play.screen().getByRole("searchbox", FILTER_NAME);
                    // The dialog focuses the filter once it is shown (deferred)
                    play.waitFor(() -> play.expect(filter).toHaveFocus());
                    play.type(filter, "al");
                    play.keyboard("{Escape}");
                    play.expect(filter).toHaveValue("");
                    play.expect(play.screen().getByRole("dialog", CAPTION)).toBeVisible();
                    play.expect(play.spy(ON_CANCEL)).not().toHaveBeenCalled();
                    play.keyboard("{Escape}");
                    play.expect(play.spy(ON_CANCEL)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ON_OK)).not().toHaveBeenCalled();
                    play.waitFor(() -> play.expect(play.screen().queryByRole("dialog")).toBeNull());
                    play.expect(opener).toHaveFocus();
                })
                // Escape in a text box cancels the dialog and goes no further: the page behind (here
                // a key handler on the page, as Stroom's own stops a dashboard's query on Escape)
                // doesn't see it
                .story("PageBehindNotTold", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    final Spy pageEscape = context.fn(PAGE_ESCAPE);
                    final HandlerRegistration registration = RootPanel.get().addDomHandler(event -> {
                        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                            pageEscape.call();
                        }
                    }, KeyDownEvent.getType());
                    popups.addCleanUp(registration::removeHandler);
                    return DialogWidgets.button(OPEN, DialogWidgets.PRIMARY, event -> {
                        final TextBox textBox = new TextBox();
                        textBox.getElement().setAttribute("aria-label", NAME);
                        show(spies, popups, textBox, () -> textBox.setFocus(true));
                    });
                })
                .withPlay(play -> {
                    play.click(play.getByRole("button", StroomDom.button(OPEN)));
                    final Query textBox = play.screen().getByRole("textbox", NAME);
                    play.waitFor(() -> play.expect(textBox).toHaveFocus());
                    play.type(textBox, "abc");
                    play.keyboard("{Escape}");
                    play.expect(play.spy(ON_CANCEL)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(PAGE_ESCAPE)).not().toHaveBeenCalled();
                    play.waitFor(() -> play.expect(play.screen().queryByRole("dialog")).toBeNull());
                })
                // A dialog that isn't modal lets the page behind be used: Escape with the focus on
                // the page still cancels the dialog, as it always has
                .story("FocusOnPage", context -> {
                    final StoryPopups popups = StoryPopups.create(context);
                    final Spy[] spies = spies(context);
                    final Widget open = DialogWidgets.button(OPEN, DialogWidgets.PRIMARY, event ->
                            show(spies, popups, StoryPanels.note("Nothing to filter.", "#888", "0.9rem"), null));
                    final Widget elsewhere = DialogWidgets.button("Elsewhere", DialogWidgets.SECONDARY, event -> {
                        // Nothing to do
                    });
                    return StoryPanels.column(12, open, elsewhere);
                })
                .withPlay(play -> {
                    play.click(play.getByRole("button", StroomDom.button(OPEN)));
                    play.expect(play.screen().getByRole("dialog", CAPTION)).toBeVisible();
                    final Query elsewhere = play.getByRole("button", StroomDom.button("Elsewhere"));
                    play.click(elsewhere);
                    play.expect(elsewhere).toHaveFocus();
                    play.keyboard("{Escape}");
                    play.expect(play.spy(ON_CANCEL)).toHaveBeenCalledTimes(1);
                    play.waitFor(() -> play.expect(play.screen().queryByRole("dialog")).toBeNull());
                });
    }

    /// The `onOk` and `onCancel` spies, registered as the story renders.
    private static Spy[] spies(final StoryContext context) {
        return new Spy[]{context.fn(ON_OK), context.fn(ON_CANCEL)};
    }

    // Shows a dialog that isn't modal, holding the content, focusing it with `focus` (if given)
    private static void show(final Spy[] spies,
                             final StoryPopups popups,
                             final Widget content,
                             final Runnable focus) {
        final Spy onOk = spies[0];
        final Spy onCancel = spies[1];
        final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(
                        new StoryContent(popups.getEventBus(), content))
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(CAPTION)
                .onHideRequest(event -> {
                    if (event.isOk()) {
                        onOk.call();
                    } else {
                        onCancel.call();
                    }
                    event.hide();
                });
        if (focus != null) {
            builder.onShow(event -> focus.run());
        }
        builder.fire();
    }
}
