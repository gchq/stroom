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

import stroom.annotation.client.SettingBlock;
import stroom.annotation.shared.AnnotationTag;
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

/// The content of Change Status: the status to set on the annotations chosen, which a press on
/// chooses.
public class ChangeStatus extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The value is shown faded when there is none (AnnotationEditor's)
    private static final String EMPTY_VALUE = "annotation-setting__value--empty";

    @UiField
    Label status;
    @UiField
    SettingBlock statusBlock;
    @UiField
    FormGroup statusGroup;

    private Consumer<Element> onChooseStatus = element -> {
    };

    /// Makes empty content.
    public ChangeStatus() {
        initWidget(BINDER.createAndBindUi(this));
        statusBlock.addClickHandler(this::onStatusBlock);
    }

    /// @param status The status to set, or null for none.
    public void setStatus(final AnnotationTag status) {
        if (status == null) {
            this.status.setText("None");
            this.status.addStyleName(EMPTY_VALUE);
        } else {
            this.status.setText(status.getName());
            this.status.removeStyleName(EMPTY_VALUE);
            statusGroup.clearFeedback();
        }
    }

    /// Says, under the status, that one must be chosen, and focuses its button.
    ///
    /// @param message What is missing, e.g. `Choose a status to set`.
    public void setStatusMissing(final String message) {
        statusGroup.setFeedback(message);
        statusBlock.focus();
    }

    /// @param onChooseStatus Given the status's element, to show the chooser at, when it is pressed.
    public void setOnChooseStatus(final Consumer<Element> onChooseStatus) {
        this.onChooseStatus = Objects.requireNonNull(onChooseStatus, "onChooseStatus");
    }

    private void onStatusBlock(final ClickEvent e) {
        onChooseStatus.accept(statusBlock.getElement());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ChangeStatus> {

    }
}
