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

package stroom.data.client.view;

import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.FabButton;
import stroom.widget.spinner.client.SpinnerLarge;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceViewImpl extends ViewImpl implements SourceView {

    private final Widget widget;

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
    FlowPanel container;
    @UiField
    SimplePanel navigatorContainer;
    @UiField
    SimplePanel progressBarPanel;
    @UiField
    FabButton steppingButton;
    @UiField
    SpinnerLarge spinner;

    @Inject
    public SourceViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        steppingButton.setIcon(SvgImage.STEPPING);
        spinner.setVisible(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    @Override
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

    @Override
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

    @Override
    public void setTextView(final View textView) {
        container.add(textView.asWidget());
    }

    @Override
    public void setNavigatorView(final View characterNavigatorView) {
        if (characterNavigatorView != null) {
            navigatorContainer.setWidget(characterNavigatorView.asWidget());
        } else {
            navigatorContainer.clear();
        }
    }

    @Override
    public void setProgressView(final View progressView) {
        if (progressView != null) {
            progressBarPanel.setWidget(progressView.asWidget());
        } else {
            progressBarPanel.clear();
        }
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

    public interface Binder extends UiBinder<Widget, SourceViewImpl> {

    }
}
