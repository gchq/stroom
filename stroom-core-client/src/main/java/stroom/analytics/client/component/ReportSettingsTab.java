/*
 * Copyright 2016 Crown Copyright
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

import stroom.analytics.shared.ReportSettings;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A report's Settings tab: the type of file it sends, whether it is sent when empty, its AI
/// summary's model and prompt, and its error feed. Its controller ([ReportSettingsTabController])
/// attaches the pickers.
public class ReportSettingsTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<DownloadSearchResultFileType> fileType;
    @UiField
    TickBox sendEmptyReports;
    @UiField
    TickBox aiSummaryEnabled;
    @UiField
    DropDownBox aiSummaryModel;
    @UiField
    TextArea aiSummaryPrompt;
    @UiField
    DropDownBox errorFeed;
    @UiField
    Button setDefaultErrorFeed;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public ReportSettingsTab() {
        initWidget(BINDER.createAndBindUi(this));

        fileType.addItems(DownloadSearchResultFileType.asSortedList());
        fileType.setValue(ReportSettings.DEFAULT_FILE_TYPE);

        fileType.addValueChangeHandler(event -> onChange.run());
        sendEmptyReports.addValueChangeHandler(event -> onChange.run());
        aiSummaryEnabled.addValueChangeHandler(event -> onChange.run());
        aiSummaryPrompt.addValueChangeHandler(event -> onChange.run());
    }

    /// @return The AI summary model's picker, for the controller to attach to.
    public DropDownBox getAiSummaryModelBox() {
        return aiSummaryModel;
    }

    /// @return The error feed's picker, for the controller to attach to.
    public DropDownBox getErrorFeedBox() {
        return errorFeed;
    }

    /// Shows a report's settings, but for its pickers' documents.
    ///
    /// @param reportSettings The report's settings, or null for the defaults.
    public void read(final ReportSettings reportSettings) {
        fileType.setValue(NullSafe.getOrElse(
                reportSettings,
                ReportSettings::getFileType,
                DownloadSearchResultFileType.EXCEL));
        sendEmptyReports.setValue(NullSafe.getOrElse(
                reportSettings,
                ReportSettings::isSendEmptyReports,
                false));
        aiSummaryEnabled.setValue(NullSafe.getOrElse(
                reportSettings,
                ReportSettings::isAiSummaryEnabled,
                false));
        // Shown empty rather than pre-filled with the default, so that reading and writing the
        // document round trips. Filling the box in would make an untouched report look dirty as
        // soon as it was opened, and would bake the default text into the document on the next
        // save.
        aiSummaryPrompt.setText(NullSafe.getOrElse(
                reportSettings,
                ReportSettings::getAiSummaryPrompt,
                ""));
    }

    /// Writes the settings shown, but for the AI summary model, into a report's settings' builder.
    ///
    /// @param builder The builder.
    /// @return The builder.
    public ReportSettings.Builder write(final ReportSettings.Builder builder) {
        final String prompt = aiSummaryPrompt.getText();
        return builder
                .fileType(fileType.getValue())
                .sendEmptyReports(sendEmptyReports.getValue())
                .aiSummaryEnabled(aiSummaryEnabled.getValue())
                // Blank means "use the default", which is what a null prompt says.
                .aiSummaryPrompt(NullSafe.isBlankString(prompt)
                        ? null
                        : prompt);
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
        fileType.setReadOnly(readOnly);
        sendEmptyReports.setReadOnly(readOnly);
        aiSummaryEnabled.setReadOnly(readOnly);
        aiSummaryPrompt.setReadOnly(readOnly);
        // Setting the default from a document the user can't change would be odd, so it is
        // disabled too
        setDefaultErrorFeed.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ReportSettingsTab> {

    }
}
