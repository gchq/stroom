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

import stroom.annotation.shared.AnnotationTag;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.Consumer;

/// An annotation's Status setting (a [SettingBlock]): the status, which a press on chooses, as
/// an annotation's editor and Change Status show it. Its help text is the screen's own.
public class AnnotationStatusSetting extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // The value is shown faded when there is none
    private static final String EMPTY_VALUE = "annotation-setting__value--empty";

    @UiField
    SettingBlock block;
    @UiField
    FormGroup group;
    @UiField
    Label status;

    private Consumer<Element> onChoose = element -> {
    };

    /// Makes the setting, showing no status.
    public AnnotationStatusSetting() {
        initWidget(BINDER.createAndBindUi(this));
        block.addClickHandler(event -> onChoose.accept(block.getElement()));
    }

    /// @param helpText What the setting is, and what pressing it does, as its help says.
    public void setHelpText(final String helpText) {
        group.setHelpText(helpText);
    }

    /// Shows the status, clearing any feedback that one is missing.
    ///
    /// @param status The status, or null for none.
    public void setStatus(final AnnotationTag status) {
        if (status == null) {
            this.status.setText("None");
            this.status.addStyleName(EMPTY_VALUE);
        } else {
            this.status.setText(status.getName());
            this.status.removeStyleName(EMPTY_VALUE);
            group.clearFeedback();
        }
    }

    /// Says, under the status, that one must be chosen, and focuses its button.
    ///
    /// @param message What is missing, e.g. `Choose a status to set`.
    public void setStatusMissing(final String message) {
        group.setFeedback(message);
        block.focus();
    }

    /// @param onChoose Given the setting's element, to show the chooser at, when it is pressed.
    public void setOnChoose(final Consumer<Element> onChoose) {
        this.onChoose = Objects.requireNonNull(onChoose, "onChoose");
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        block.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnnotationStatusSetting> {

    }
}
