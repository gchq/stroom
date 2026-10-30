/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleLevel;
import stroom.analytics.shared.AnalyticRuleStatus;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// An analytic rule's Settings tab: its level and status, whether its documentation goes with
/// its detections, and its error feed, whose picker its controller ([AnalyticSettingsController])
/// attaches to.
public class AnalyticSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<AnalyticRuleLevel> level;
    @UiField
    SelectionBox<AnalyticRuleStatus> status;
    @UiField
    TickBox includeRuleDocumentation;
    @UiField
    DropDownBox errorFeed;
    @UiField
    Button setDefaultErrorFeed;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public AnalyticSettings() {
        initWidget(BINDER.createAndBindUi(this));

        // A rule need not declare a level or a status, so both offer a blank item to go back to.
        level.setNonSelectString("");
        level.addItems(AnalyticRuleLevel.values());
        status.setNonSelectString("");
        status.addItems(AnalyticRuleStatus.values());

        level.addValueChangeHandler(event -> onChange.run());
        status.addValueChangeHandler(event -> onChange.run());
        includeRuleDocumentation.addValueChangeHandler(event -> onChange.run());
    }

    /// @return The error feed's picker, for the controller to attach to.
    public DropDownBox getErrorFeedBox() {
        return errorFeed;
    }

    /// Shows a rule's settings, but for its error feed.
    ///
    /// @param rule The rule.
    public void read(final AnalyticRuleDoc rule) {
        level.setValue(rule.getLevel());
        status.setValue(rule.getStatus());
        includeRuleDocumentation.setValue(rule.isIncludeRuleDocumentation());
    }

    /// Writes the settings shown, but for the error feed, into a rule's builder.
    ///
    /// @param builder The rule's builder.
    /// @return The builder.
    public AnalyticRuleDoc.Builder write(final AnalyticRuleDoc.Builder builder) {
        return builder
                .level(level.getValue())
                .status(status.getValue())
                .includeRuleDocumentation(includeRuleDocumentation.getValue());
    }

    /// @param visible Whether Set Default is shown, to administrators only.
    public void setSetDefaultVisible(final boolean visible) {
        setDefaultErrorFeed.setVisible(visible);
    }

    /// @param onSetDefault What to run when Set Default is pressed.
    public void setOnSetDefault(final Runnable onSetDefault) {
        setDefaultErrorFeed.setOnClick(onSetDefault);
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        level.setReadOnly(readOnly);
        status.setReadOnly(readOnly);
        includeRuleDocumentation.setReadOnly(readOnly);
        // Setting the default from a document the user can't change would be odd, so it is
        // disabled too
        setDefaultErrorFeed.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnalyticSettings> {

    }
}
