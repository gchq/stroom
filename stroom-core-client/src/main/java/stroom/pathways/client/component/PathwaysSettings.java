/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.pathways.shared.PathwaysDoc;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.duration.client.DurationPicker;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A pathways document's Settings tab: its trace store and info feed, whose pickers its controller
/// ([PathwaysSettingsController]) attaches to, the tolerance of the traces' ordering, what may be
/// created and changed, and the node that processes the traces.
public class PathwaysSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox traceStore;
    @UiField
    DropDownBox infoFeed;
    @UiField
    DurationPicker temporalOrderingTolerance;
    @UiField
    TickBox allowPathwayCreation;
    @UiField
    TickBox allowPathwayMutation;
    @UiField
    TickBox allowConstraintCreation;
    @UiField
    TickBox allowConstraintMutation;
    @UiField
    TextBox processingNode;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public PathwaysSettings() {
        initWidget(BINDER.createAndBindUi(this));
        temporalOrderingTolerance.smallTimeMode();
        temporalOrderingTolerance.setValue(new SimpleDuration(0L, TimeUnit.NANOSECONDS));

        temporalOrderingTolerance.addValueChangeHandler(e -> onChange.run());
        allowPathwayCreation.addValueChangeHandler(e -> onChange.run());
        allowPathwayMutation.addValueChangeHandler(e -> onChange.run());
        allowConstraintCreation.addValueChangeHandler(e -> onChange.run());
        allowConstraintMutation.addValueChangeHandler(e -> onChange.run());
        processingNode.addValueChangeHandler(e -> onChange.run());
    }

    /// @return The trace store's picker, for the controller to attach to.
    public DropDownBox getTraceStoreBox() {
        return traceStore;
    }

    /// @return The info feed's picker, for the controller to attach to.
    public DropDownBox getInfoFeedBox() {
        return infoFeed;
    }

    /// Shows a pathways document's settings, but for its trace store and info feed.
    ///
    /// @param doc The document.
    public void read(final PathwaysDoc doc) {
        temporalOrderingTolerance.setValue(doc.getTemporalOrderingTolerance() == null
                ? new SimpleDuration(0L, TimeUnit.NANOSECONDS)
                : doc.getTemporalOrderingTolerance());
        allowPathwayCreation.setValue(doc.isAllowPathwayCreation());
        allowPathwayMutation.setValue(doc.isAllowPathwayMutation());
        allowConstraintCreation.setValue(doc.isAllowConstraintCreation());
        allowConstraintMutation.setValue(doc.isAllowConstraintMutation());
        processingNode.setValue(doc.getProcessingNode());
    }

    /// Writes the settings shown, but for the trace store and info feed, into a document's builder.
    ///
    /// @param builder The document's builder.
    /// @return The builder.
    public PathwaysDoc.Builder write(final PathwaysDoc.Builder builder) {
        return builder
                .temporalOrderingTolerance(temporalOrderingTolerance.getValue())
                .allowPathwayCreation(allowPathwayCreation.getValue())
                .allowPathwayMutation(allowPathwayMutation.getValue())
                .allowConstraintCreation(allowConstraintCreation.getValue())
                .allowConstraintMutation(allowConstraintMutation.getValue())
                .processingNode(processingNode.getValue());
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // Every setting: before, only the tolerance, and edits to the rest of a document that can't
        // be saved were lost (the pickers are the controller's)
        temporalOrderingTolerance.setEnabled(!readOnly);
        allowPathwayCreation.setReadOnly(readOnly);
        allowPathwayMutation.setReadOnly(readOnly);
        allowConstraintCreation.setReadOnly(readOnly);
        allowConstraintMutation.setReadOnly(readOnly);
        processingNode.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PathwaysSettings> {

    }
}
