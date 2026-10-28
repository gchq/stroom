/*
 * Copyright 2022 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.shared.LayoutConstraints;
import stroom.widget.component.client.Component;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The Set Layout Constraints dialog's content: whether a dashboard fits the window's width and
/// height.
public class LayoutConstraint extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TickBox fitWidth;
    @UiField
    TickBox fitHeight;

    private Consumer<LayoutConstraints> onChange = constraints -> {
    };

    /// Makes the content with neither ticked.
    public LayoutConstraint() {
        initWidget(BINDER.createAndBindUi(this));
        fitWidth.setOnChange(value -> onChange.accept(getConstraints()));
        fitHeight.setOnChange(value -> onChange.accept(getConstraints()));
    }

    /// Puts the focus on Fit Width.
    public void focus() {
        fitWidth.setFocus(true);
    }

    /// @param constraints The constraints to show, or null to leave them as they are.
    public void setConstraints(final LayoutConstraints constraints) {
        if (constraints != null) {
            fitWidth.setValue(constraints.isFitWidth());
            fitHeight.setValue(constraints.isFitHeight());
        }
    }

    /// @return The constraints ticked.
    public LayoutConstraints getConstraints() {
        return new LayoutConstraints(fitWidth.getValue(), fitHeight.getValue());
    }

    /// @param onChange Given the constraints whenever the user ticks or unticks one.
    public void setOnChange(final Consumer<LayoutConstraints> onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, LayoutConstraint> {

    }
}
