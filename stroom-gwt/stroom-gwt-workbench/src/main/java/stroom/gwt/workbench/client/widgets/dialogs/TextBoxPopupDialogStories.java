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
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.button.client.Button;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.presenter.TextBoxPopup;
import stroom.widget.popup.client.view.TextBoxViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;

/// Stories for Stroom's [TextBoxPopup], matching `Widgets/Dialogs/TextBoxPopupDialog` in the
/// React Storybook.
public final class TextBoxPopupDialogStories {

    // The React TextBoxPopupDialog's callback props
    private static final String ON_OK = "onOk";
    private static final String ON_CANCEL = "onCancel";

    private TextBoxPopupDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Dialogs/TextBoxPopupDialog", TextBoxPopupDialogStories.class)
                .layout(StoryLayout.CENTERED)
                // Open the popup, edit the text, then OK (accepts only non-blank) or Cancel
                .story("Basic", context -> {
                    final Spy onOk = context.fn(ON_OK);
                    final Spy onCancel = context.fn(ON_CANCEL);
                    final StoryPopups popups = StoryPopups.create(context);
                    final TextBoxPopup textBoxPopup = new TextBoxPopup(popups.getEventBus(),
                            new TextBoxViewImpl(GWT.create(TextBoxViewImpl.Binder.class)));
                    // Differs from React: TextBoxPopup has no cancel callback (it just hides), so
                    // the story reports Cancel from the popup's HidePopupEvent.
                    popups.addCleanUp(popups.getEventBus().addHandler(HidePopupEvent.getType(), event -> {
                        if (event.getPresenterWidget() == textBoxPopup && !event.isOk()) {
                            onCancel.call();
                        }
                    })::removeHandler);

                    final InlineLabel result = new InlineLabel("Result: —");
                    result.getElement().getStyle().setProperty("fontSize", "12px");
                    final Button button = DialogWidgets.button("Open popup", DialogWidgets.PRIMARY, event -> {
                        textBoxPopup.setText("current name");
                        textBoxPopup.show("Rename", value -> {
                            onOk.call(value);
                            result.setText("Result: " + value);
                        });
                    });

                    final FlowPanel column = StoryPanels.column(8, button, result);
                    column.getElement().getStyle().setProperty("alignItems", "center");
                    return column;
                });
    }
}
