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


package stroom.gwt.workbench.client.widgets.display;

import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for the Stroom infinity logo, shown at the top of the login form, matching
/// `Widgets/Display/InfinityLogo` in the React Storybook.
///
/// There is no logo widget in GWT: the stories build it as Stroom's `LoginViewImpl` does, an
/// [SvgImage#INFINITY_LOGO] in a `LoginViewUserImage` panel inside a
/// `LoginViewUserImageContainer` panel.
public final class InfinityLogoStories {

    // Arg names, the same as the React InfinityLogo's props
    private static final String SIZE = "size";

    private InfinityLogoStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Display/InfinityLogo", InfinityLogoStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.text(SIZE).description("The width and height of the image, e.g. '120px' "
                                                        + "(in Stroom, the login form's CSS sizes it)."))
                // Sized explicitly (as used outside the login form)
                .story("Default", InfinityLogoStories::fromArgs)
                .withArgs(Args.of(SIZE, "120px"))
                // A larger rendering
                .story("Large", InfinityLogoStories::fromArgs)
                .withArgs(Args.of(SIZE, "240px"));
    }

    /// A logo made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        // As LoginViewImpl's UiBinder template and constructor build it
        final SimplePanel userImage = new SimplePanel();
        userImage.setStyleName("LoginViewUserImage");
        userImage.getElement().setInnerHTML(SvgImage.INFINITY_LOGO.getSvg());
        userImage.addStyleName(SvgImage.INFINITY_LOGO.getClassName());
        final String size = context.getArgs().getString(SIZE, "");
        if (!size.isEmpty()) {
            userImage.getElement().getStyle().setProperty("width", size);
            userImage.getElement().getStyle().setProperty("height", size);
        }

        final SimplePanel container = new SimplePanel(userImage);
        container.setStyleName("LoginViewUserImageContainer");
        return container;
    }
}
