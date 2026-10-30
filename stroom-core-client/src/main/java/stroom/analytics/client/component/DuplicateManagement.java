/*
 * Copyright 2023 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/// An analytic rule's Duplicate Management tab: whether its notifications are remembered and
/// duplicates suppressed, which columns are compared, and below them the remembered duplicates (a
/// list its controller shows here).
public class DuplicateManagement extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TickBox rememberNotifications;
    @UiField
    TickBox suppressDuplicateNotifications;
    @UiField
    TickBox chooseColumns;
    @UiField
    TextBox columns;
    @UiField
    DuplicateManagementList list;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, with nothing ticked and no list.
    public DuplicateManagement() {
        initWidget(BINDER.createAndBindUi(this));
        rememberNotifications.addValueChangeHandler(e -> onChange.run());
        suppressDuplicateNotifications.addValueChangeHandler(e -> onChange.run());
        chooseColumns.addValueChangeHandler(e -> {
            columns.setEnabled(chooseColumns.getValue());
            onChange.run();
        });
        columns.addValueChangeHandler(e -> onChange.run());
    }

    /// Shows a rule's duplicate notification settings.
    ///
    /// @param config The settings.
    public void read(final DuplicateNotificationConfig config) {
        rememberNotifications.setValue(config.isRememberNotifications());
        suppressDuplicateNotifications.setValue(config.isSuppressDuplicateNotifications());
        chooseColumns.setValue(config.isChooseColumns());
        columns.setEnabled(config.isChooseColumns());
        columns.setValue(String.join(", ", config.getColumnNames()));
    }

    /// @return The duplicate notification settings shown, with the columns as a list of the names
    /// entered between commas.
    public DuplicateNotificationConfig write() {
        final String[] arr = NullSafe.string(columns.getValue())
                .split(",");
        final List<String> columnNames = Arrays.stream(arr)
                .map(String::trim)
                .filter(NullSafe::isNonEmptyString)
                .collect(Collectors.toList());
        return new DuplicateNotificationConfig(rememberNotifications.getValue(),
                suppressDuplicateNotifications.getValue(),
                chooseColumns.getValue(),
                columnNames);
    }

    /// @return The remembered duplicates, which its controller attaches the list's controller to.
    public DuplicateManagementList getList() {
        return list;
    }

    /// @param onChange Run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        rememberNotifications.setReadOnly(readOnly);
        suppressDuplicateNotifications.setReadOnly(readOnly);
        chooseColumns.setReadOnly(readOnly);
        columns.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DuplicateManagement> {

    }
}
