/*
 * Copyright 2019 Crown Copyright
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
import stroom.annotation.client.SettingBlock;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// The content of Change Assigned To: who to assign the annotations chosen to, which a press on
/// chooses, and Assign Yourself.
public class ChangeAssignee extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The value is shown faded when there is none (AnnotationEditor's)
    private static final String EMPTY_VALUE = "annotation-setting__value--empty";

    @UiField
    Label assignedTo;
    @UiField
    LinkButton assignYourself;
    @UiField
    SettingBlock assignedToBlock;

    private Runnable onChooseAssignedTo = () -> {
    };
    private Runnable onAssignYourself = () -> {
    };

    /// Makes empty content.
    public ChangeAssignee() {
        initWidget(BINDER.createAndBindUi(this));
        assignedToBlock.addClickHandler(this::onAssignedToBlock);
        // The setting around it opens its chooser on a click: this click (or Enter or Space) is the
        // button's own
        assignYourself.addClickHandler(ClickEvent::stopPropagation);
        assignYourself.setOnClick(() -> onAssignYourself.run());
    }

    /// @param assignedTo Who to assign the annotations to, or null for nobody.
    public void setAssignedTo(final UserRef assignedTo) {
        if (assignedTo == null) {
            this.assignedTo.setText("Nobody");
            this.assignedTo.addStyleName(EMPTY_VALUE);
        } else {
            this.assignedTo.setText(assignedTo.toDisplayString());
            this.assignedTo.removeStyleName(EMPTY_VALUE);
        }
    }

    /// Enables Assign Yourself, or disables it, saying why, when the annotations are already to
    /// be assigned to the current user.
    ///
    /// @param enabled Whether it is enabled.
    public void setAssignYourselfEnabled(final boolean enabled) {
        AssignYourself.setState(assignYourself, !enabled, false);
    }

    /// @param onChooseAssignedTo Run when who to assign the annotations to is pressed.
    public void setOnChooseAssignedTo(final Runnable onChooseAssignedTo) {
        this.onChooseAssignedTo = Objects.requireNonNull(onChooseAssignedTo, "onChooseAssignedTo");
    }

    /// @param onAssignYourself Run when Assign Yourself is pressed.
    public void setOnAssignYourself(final Runnable onAssignYourself) {
        this.onAssignYourself = Objects.requireNonNull(onAssignYourself, "onAssignYourself");
    }

    private void onAssignedToBlock(final ClickEvent e) {
        onChooseAssignedTo.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ChangeAssignee> {

    }
}
