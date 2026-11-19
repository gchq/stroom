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

package stroom.annotation.client.component;

import stroom.annotation.client.AssignYourself;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/// An annotation's Assigned To setting (a [SettingBlock]): who it is assigned to, which a press on
/// chooses, and Assign Yourself, as an annotation's editor and Change Assigned To show it. Its help
/// text is the screen's own.
public class AnnotationAssignedToSetting extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The value is shown faded when there is none
    private static final String EMPTY_VALUE = "annotation-setting__value--empty";

    @UiField
    SettingBlock block;
    @UiField
    FormGroup group;
    @UiField
    Label assignedTo;
    @UiField
    LinkButton assignYourself;

    private boolean assignYourselfEnabled;
    private Consumer<Element> onChoose = element -> {
    };
    private Consumer<Element> onAssignYourself = element -> {
    };

    /// Makes the setting, showing nobody.
    public AnnotationAssignedToSetting() {
        initWidget(BINDER.createAndBindUi(this));
        block.addClickHandler(event -> onChoose.accept(block.getElement()));
        // The setting around it opens its chooser on a click: this click (or Enter or Space) is the
        // button's own
        assignYourself.addClickHandler(ClickEvent::stopPropagation);
        assignYourself.setOnClick(() -> onAssignYourself.accept(assignYourself.getElement()));
    }

    /// @param helpText What the setting is, and what pressing it does, as its help says.
    public void setHelpText(final String helpText) {
        group.setHelpText(helpText);
    }

    /// @param assignedTo Who it is assigned to, or null for nobody.
    public void setAssignedTo(final UserRef assignedTo) {
        if (assignedTo == null) {
            this.assignedTo.setText("Nobody");
            this.assignedTo.addStyleName(EMPTY_VALUE);
        } else {
            this.assignedTo.setText(assignedTo.toDisplayString());
            this.assignedTo.removeStyleName(EMPTY_VALUE);
        }
    }

    /// Enables Assign Yourself, or disables it, saying why, when it is already assigned to the
    /// current user. It is disabled too while the setting is read only.
    ///
    /// @param enabled Whether it is enabled.
    public void setAssignYourselfEnabled(final boolean enabled) {
        this.assignYourselfEnabled = enabled;
        updateAssignYourself();
    }

    /// @param onChoose Given the setting's element, to show the chooser at, when it is pressed.
    public void setOnChoose(final Consumer<Element> onChoose) {
        this.onChoose = Objects.requireNonNull(onChoose, "onChoose");
    }

    /// @param onAssignYourself Given Assign Yourself's element when it is pressed.
    public void setOnAssignYourself(final Consumer<Element> onAssignYourself) {
        this.onAssignYourself = Objects.requireNonNull(onAssignYourself, "onAssignYourself");
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        block.setReadOnly(readOnly);
        updateAssignYourself();
    }

    private void updateAssignYourself() {
        AssignYourself.setState(assignYourself, !assignYourselfEnabled, isReadOnly());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnnotationAssignedToSetting> {

    }
}
