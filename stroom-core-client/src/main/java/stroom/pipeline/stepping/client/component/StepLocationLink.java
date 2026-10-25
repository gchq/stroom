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

package stroom.pipeline.stepping.client.component;

import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.util.client.DisabledState;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;

/// The stepping screen's location, e.g. `[123:1:4]`: a link that asks to open the "Set Location"
/// dialog. The BEM block `step-location-link` (in `stepping-screen.css`), its markup
/// `StepLocationLink.ui.xml`; [StepLocationLinkController] fills it.
public class StepLocationLink extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    LinkButton button;

    /// Makes a link with no location chosen.
    public StepLocationLink() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param onClick Run when the link is pressed, even while it is disabled (its controller
    ///                decides).
    public void setOnClick(final Runnable onClick) {
        button.setOnClick(onClick);
    }

    /// Shows a location.
    ///
    /// @param text      The location as a code, e.g. `[123:1:4]`.
    /// @param ariaLabel What the link is named, as the code alone says little.
    public void setLocation(final String text, final String ariaLabel) {
        button.setText(text);
        button.setAriaLabel(ariaLabel);
    }

    /// Shows whether the location can be set: disabled, it stays focusable so its name can still
    /// be found, and is greyed (`aria-disabled`).
    ///
    /// @param enabled Whether the location can be set.
    public void setEnabled(final boolean enabled) {
        DisabledState.set(button.getElement(), !enabled);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<LinkButton, StepLocationLink> {

    }
}
