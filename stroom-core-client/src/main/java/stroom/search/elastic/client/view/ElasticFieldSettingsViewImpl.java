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

package stroom.search.elastic.client.view;

import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.item.client.BaseSelectionBox;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.search.elastic.client.presenter.ElasticFieldSettingsPresenter.ElasticFieldSettingsView;
import stroom.search.elastic.client.presenter.ElasticFieldSettingsUiHandlers;
import stroom.util.shared.NullSafe;
import stroom.widget.help.client.HelpButton;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

/// The view of the Settings tab of the Elasticsearch index Fields tab.
public class ElasticFieldSettingsViewImpl
        extends ViewWithUiHandlers<ElasticFieldSettingsUiHandlers>
        implements ElasticFieldSettingsView {

    private static final String ENABLED_HELP = "When unticked, the field is hidden from users and can't be used in " +
                                               "queries. Existing queries that use it fail with an error. " +
                                               "Disabling a field also disables its subfields.";

    private final Widget widget;
    private final FieldNameSelectionListModel rerankTextFieldModel = new FieldNameSelectionListModel();

    @UiField
    Label fieldName;
    @UiField
    FlowPanel enabledRow;
    @UiField
    CustomCheckBox enabled;
    @UiField
    Label enabledNote;
    @UiField
    FlowPanel denseVectorSection;
    @UiField
    SimplePanel embeddingModel;
    @UiField
    ValueSpinner nearestNeighbourCount;
    @UiField
    SimplePanel rerankModel;
    @UiField
    ValueSpinner rerankBatchSize;
    @UiField
    BaseSelectionBox<String, SimpleSelectionItemWrapper<String>> rerankTextField;
    @UiField
    TextBox rerankScoreField;
    @UiField
    TextBox rerankScoreMinimum;
    @UiField
    Label rerankScoreMinimumError;
    @UiField
    FlowPanel virtualSection;
    @UiField
    SimplePanel groups;

    @Inject
    public ElasticFieldSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        nearestNeighbourCount.setMin(0L);
        nearestNeighbourCount.setMax(10_000L);
        rerankBatchSize.setMin(1L);
        rerankBatchSize.setMax(100_000L);
        // Filterable and paged, as an index can have many text fields
        rerankTextField.setModel(rerankTextFieldModel);
        rerankTextFieldModel.setNonSelectString(DocSelectionBoxPresenter.NONE_DISPLAY_VALUE);

        final HelpButton enabledHelp = HelpButton.create();
        enabledHelp.setHelpContent("Enabled", new SafeHtmlBuilder()
                .appendHtmlConstant("<p>")
                .appendEscaped(ENABLED_HELP)
                .appendHtmlConstant("</p>")
                .toSafeHtml());
        enabledRow.add(enabledHelp);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setFieldName(final String fieldName) {
        setMessage(this.fieldName, fieldName);
    }

    @Override
    public void setEnabled(final boolean ticked, final boolean editable, final String note) {
        enabled.setValue(ticked);
        enabled.setEnabled(editable);
        setMessage(enabledNote, note);
    }

    @Override
    public boolean isEnabledTicked() {
        return Boolean.TRUE.equals(enabled.getValue());
    }

    @Override
    public void setDenseVectorVisible(final boolean visible) {
        denseVectorSection.setVisible(visible);
    }

    @Override
    public void setEmbeddingModelView(final View view) {
        embeddingModel.setWidget(view.asWidget());
    }

    @Override
    public void setRerankModelView(final View view) {
        rerankModel.setWidget(view.asWidget());
    }

    @Override
    public void setRerankTextFieldOptions(final List<String> fieldNames) {
        rerankTextFieldModel.clear();
        // Clearing removes the 'None' item too, so add it back first
        rerankTextFieldModel.setNonSelectString(DocSelectionBoxPresenter.NONE_DISPLAY_VALUE);
        rerankTextFieldModel.addItems(fieldNames);
    }

    @Override
    public void setDenseVectorValues(final int nearestNeighbourCount,
                                     final int rerankBatchSize,
                                     final String rerankTextField,
                                     final String rerankScoreField,
                                     final String rerankScoreMinimum) {
        this.nearestNeighbourCount.setValue(nearestNeighbourCount);
        this.rerankBatchSize.setValue(rerankBatchSize);
        this.rerankTextField.setValue(rerankTextField);
        this.rerankScoreField.setValue(NullSafe.string(rerankScoreField));
        this.rerankScoreMinimum.setValue(NullSafe.string(rerankScoreMinimum));
    }

    @Override
    public int getNearestNeighbourCount() {
        return nearestNeighbourCount.getIntValue();
    }

    @Override
    public int getRerankBatchSize() {
        return rerankBatchSize.getIntValue();
    }

    @Override
    public String getRerankTextField() {
        return rerankTextField.getValue();
    }

    @Override
    public String getRerankScoreField() {
        return rerankScoreField.getValue();
    }

    @Override
    public String getRerankScoreMinimumText() {
        return rerankScoreMinimum.getValue();
    }

    @Override
    public void setRerankScoreMinimumError(final String error) {
        setMessage(rerankScoreMinimumError, error);
    }

    @Override
    public void setDenseVectorEditable(final boolean editable) {
        nearestNeighbourCount.setEnabled(editable);
        rerankBatchSize.setEnabled(editable);
        rerankTextField.setEnabled(editable);
        rerankScoreField.setEnabled(editable);
        rerankScoreMinimum.setEnabled(editable);
    }

    @Override
    public void setVirtualVisible(final boolean visible) {
        virtualSection.setVisible(visible);
    }

    @Override
    public void setGroupsView(final View view) {
        groups.setWidget(view.asWidget());
    }

    private static void setMessage(final Label label, final String message) {
        label.setText(NullSafe.string(message));
        label.setVisible(NullSafe.isNonBlankString(message));
    }

    @UiHandler("enabled")
    void onEnabledChange(final ValueChangeEvent<Boolean> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onEnabledChange();
        }
    }

    @UiHandler("nearestNeighbourCount")
    void onNearestNeighbourCountChange(final ValueChangeEvent<Long> event) {
        onDenseVectorChange();
    }

    @UiHandler("rerankBatchSize")
    void onRerankBatchSizeChange(final ValueChangeEvent<Long> event) {
        onDenseVectorChange();
    }

    @UiHandler("rerankTextField")
    void onRerankTextFieldChange(final ValueChangeEvent<String> event) {
        onDenseVectorChange();
    }

    @UiHandler("rerankScoreField")
    void onRerankScoreFieldChange(final ValueChangeEvent<String> event) {
        onDenseVectorChange();
    }

    @UiHandler("rerankScoreMinimum")
    void onRerankScoreMinimumChange(final ValueChangeEvent<String> event) {
        onDenseVectorChange();
    }

    private void onDenseVectorChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onDenseVectorChange();
        }
    }

    /// UiBinder for [ElasticFieldSettingsViewImpl].
    public interface Binder extends UiBinder<Widget, ElasticFieldSettingsViewImpl> {

    }
}
