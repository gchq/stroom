/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.client.component;

import stroom.docref.HasDisplayValue;
import stroom.item.client.SelectionBox;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The content of the dialog that adds users or a group: a choice of what to add, and the form
/// for it (a group's name, one identity provider user, or several).
public class CreateUser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup createTypesFormGroup;
    @UiField
    SelectionBox<CreateType> createTypes;
    @UiField
    FlowPanel panel;
    @UiField
    CreateExternalUser externalUser;
    @UiField
    CreateMultipleUsers multipleUsers;

    private Widget groupForm;
    private Consumer<CreateType> onTypeChange = type -> {
    };

    /// Makes empty content.
    public CreateUser() {
        initWidget(BINDER.createAndBindUi(this));
        createTypes.addValueChangeHandler(this::onCreateTypes);
    }

    /// Adds the form for a group's name, shown when a group is to be added.
    ///
    /// @param form The form, its controller's widget.
    public void setGroupForm(final IsWidget form) {
        groupForm = form.asWidget();
        groupForm.setVisible(false);
        panel.insert(groupForm, 0);
    }

    /// @return The form for one identity provider user, for its controller to attach to.
    public CreateExternalUser getExternalUser() {
        return externalUser;
    }

    /// @return The form for several identity provider users, for its controller to attach to.
    public CreateMultipleUsers getMultipleUsers() {
        return multipleUsers;
    }

    /// @param visible Whether the choice of what to add is shown.
    public void setCreateTypesVisible(final boolean visible) {
        createTypesFormGroup.setVisible(visible);
    }

    /// @param createTypes What can be added.
    public void setCreateTypes(final List<CreateType> createTypes) {
        this.createTypes.clear();
        this.createTypes.addItems(createTypes);
    }

    /// Chooses what is added, and shows its form.
    ///
    /// @param createType What is added.
    public void setCreateType(final CreateType createType) {
        createTypes.setValue(createType);
        showForm(createType);
    }

    /// @return What is added.
    public CreateType getCreateType() {
        return createTypes.getValue();
    }

    /// @param onTypeChange Given what is to be added when the user chooses another, once its
    ///                     form is shown.
    public void setOnTypeChange(final Consumer<CreateType> onTypeChange) {
        this.onTypeChange = Objects.requireNonNull(onTypeChange, "onTypeChange");
    }

    private void showForm(final CreateType createType) {
        if (groupForm != null) {
            groupForm.setVisible(createType == CreateType.USER_GROUP);
        }
        externalUser.setVisible(createType == CreateType.IDP_USER);
        multipleUsers.setVisible(createType == CreateType.MULTI_IDP_USERS);
    }

    private void onCreateTypes(final ValueChangeEvent<CreateType> event) {
        showForm(event.getValue());
        onTypeChange.accept(event.getValue());
    }


    // --------------------------------------------------------------------------------


    /// What the dialog adds.
    public enum CreateType implements HasDisplayValue {

        USER_GROUP("Add User Group"),
        IDP_USER("Add Identity Provider User"),
        MULTI_IDP_USERS("Add Multiple Identity Provider Users");

        private final String displayValue;

        CreateType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CreateUser> {

    }
}
