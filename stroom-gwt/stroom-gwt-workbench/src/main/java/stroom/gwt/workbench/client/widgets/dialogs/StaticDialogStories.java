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

import stroom.gwt.workbench.client.widgets.ContentPresenter;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

/// Stories for the dialogs of Stroom's identity provider screens (change password, current
/// password, reset password), matching `Widgets/Dialogs/StaticDialog` in the React Storybook.
///
/// Differs from React: Stroom shows these screens (`ChangePasswordPresenter`,
/// `CurrentPasswordPresenter`, `EmailResetPasswordPresenter`) as ordinary modal `OK_CANCEL_DIALOG`
/// popups with a `PASSWORD` icon, which is what this story does; React's `StaticDialog` is a
/// static, non-modal card that only looks like one.
public final class StaticDialogStories {

    // The React StaticDialog's callback props
    private static final String ON_OK = "onOk";
    private static final String ON_CANCEL = "onCancel";

    private StaticDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's decorator only gives the card a full-height page background to sit on
        registry.component("Widgets/Dialogs/StaticDialog", StaticDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A centred dialog with a title bar and OK/Cancel
                .story("Default", context -> {
                    final Spy onOk = context.fn(ON_OK);
                    final Spy onCancel = context.fn(ON_CANCEL);
                    final StoryPopups popups = StoryPopups.create(context);

                    final Label body = new Label("Static, non-modal card body.");
                    final Style style = body.getElement().getStyle();
                    style.setProperty("padding", "12px");
                    style.setProperty("color", "var(--text-color)");
                    final ContentPresenter presenter = new ContentPresenter(popups.getEventBus(), body);

                    // As the IdP presenters show themselves (titleOverride and okLabel are React-only)
                    ShowPopupEvent.builder(presenter)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .icon(SvgImage.PASSWORD)
                            .caption("Change Password")
                            .modal(true)
                            .onHideRequest(event -> {
                                // The story's page stays behind it, as the sign-in page does, so
                                // the dialog stays open
                                if (event.isOk()) {
                                    onOk.call();
                                } else {
                                    onCancel.call();
                                }
                                event.reset();
                            })
                            .fire();
                    return new FlowPanel();
                });
    }
}
