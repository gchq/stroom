/*
 * Copyright 2020 Crown Copyright
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

package stroom.data.client.component;

import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Component;
import stroom.widget.progress.client.component.SourceProgressBar;
import stroom.widget.spinner.client.SpinnerLarge;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A stream's source: the streams being stepped through (when stepping), its feed, id, part or
/// record and type, its navigator and progress, and its text (shown here by its controller) with
/// its classification below it, and a spinner while it loads.
public class SourceViewer extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label lblFeed;
    @UiField
    Label lblId;
    @UiField
    Label lblPartNoHeading;
    @UiField
    Label lblPartNo;
    @UiField
    Label lblSegmentNoHeading;
    @UiField
    Label lblSegmentNo;
    @UiField
    Label lblType;
    @UiField
    CharacterNavigator navigator;
    @UiField
    SourceProgressBar progressBar;
    @UiField
    SpinnerLarge spinner;
    @UiField
    ClassificationWrapper classification;
    @UiField
    DataTextPane text;
    @UiField
    SimplePanel metaListContainer;

    /// Makes the viewer, with no source.
    public SourceViewer() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows which part of a stream that isn't split into records is shown.
    ///
    /// @param feedName The stream's feed.
    /// @param id       The stream's id.
    /// @param partNo   The part, from one.
    /// @param type     The stream's type.
    public void setNonSegmentedTitle(final String feedName,
                                     final long id,
                                     final long partNo,
                                     final String type) {
        lblFeed.setText(feedName);
        lblId.setText(Long.toString(id));
        lblPartNoHeading.setVisible(true);
        lblPartNo.setVisible(true);
        lblPartNo.setText(Long.toString(partNo));
        lblSegmentNoHeading.setVisible(false);
        lblSegmentNo.setVisible(false);
        lblType.setText(type);
    }

    /// Shows which record of a stream split into records is shown.
    ///
    /// @param feedName  The stream's feed.
    /// @param id        The stream's id.
    /// @param segmentNo The record, from one.
    /// @param type      The stream's type.
    public void setSegmentedTitle(final String feedName,
                                  final long id,
                                  final long segmentNo,
                                  final String type) {
        lblFeed.setText(feedName);
        lblId.setText(Long.toString(id));
        lblPartNoHeading.setVisible(false);
        lblPartNo.setVisible(false);
        lblSegmentNoHeading.setVisible(true);
        lblSegmentNo.setVisible(true);
        lblSegmentNo.setText(Long.toString(segmentNo));
        lblType.setText(type);
    }

    /// @return The source's text, which its controller attaches the pane's controller to.
    public DataTextPane getText() {
        return text;
    }

    /// @param classification The data's classification, or blank for none.
    public void setClassification(final String classification) {
        this.classification.setClassification(classification);
    }

    /// @return The classification banner, for a controller to give its colours.
    public ClassificationLabel getClassificationLabel() {
        return classification.getClassificationLabel();
    }

    /// @return The navigator of the source's characters, which its controller attaches the
    /// navigator's controller to.
    public CharacterNavigator getNavigator() {
        return navigator;
    }

    /// @param metaList The streams being stepped through, or null for none.
    public void setMetaList(final IsWidget metaList) {
        if (metaList != null) {
            metaListContainer.setWidget(metaList);
        } else {
            metaListContainer.clear();
        }
    }

    /// @return The bar showing how far through the stream the source shown is, hidden until there
    /// is progress to show.
    public SourceProgressBar getProgressBar() {
        return progressBar;
    }

    /// @return The spinner shown while the source loads.
    public TaskMonitorFactory getSpinner() {
        return spinner;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SourceViewer> {

    }
}
