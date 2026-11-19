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

import stroom.analytics.shared.AnalyticProcessType;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// An analytic rule's or report's Execution tab: how the rule runs (its processing type), above
/// that type's settings (shown here by its controller).
public class AnalyticProcessing extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<AnalyticProcessType> processingType;
    @UiField
    SimplePanel processSettings;

    private Consumer<AnalyticProcessType> onProcessingTypeChange = processingType -> {
    };

    /// Makes the tab, offering no processing types until they are set.
    public AnalyticProcessing() {
        initWidget(BINDER.createAndBindUi(this));
        processingType.addValueChangeHandler(event ->
                onProcessingTypeChange.accept(processingType.getValue()));
    }

    /// @param processingTypes The processing types the document can use.
    public void setProcessingTypes(final List<AnalyticProcessType> processingTypes) {
        processingType.clear();
        processingType.addItems(processingTypes);
    }

    /// @return The processing type chosen.
    public AnalyticProcessType getProcessingType() {
        return processingType.getValue();
    }

    /// @param analyticProcessType The processing type to show as chosen.
    public void setProcessingType(final AnalyticProcessType analyticProcessType) {
        processingType.setValue(analyticProcessType);
    }

    /// @param settings The chosen processing type's settings.
    public void setProcessSettings(final IsWidget settings) {
        processSettings.setWidget(settings);
    }

    /// @param onProcessingTypeChange Given the processing type the user chooses.
    public void setOnProcessingTypeChange(final Consumer<AnalyticProcessType> onProcessingTypeChange) {
        this.onProcessingTypeChange = onProcessingTypeChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        processingType.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnalyticProcessing> {

    }
}
