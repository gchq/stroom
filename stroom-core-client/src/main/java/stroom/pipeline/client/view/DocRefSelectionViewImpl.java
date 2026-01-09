/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.client.view;

import stroom.pipeline.client.presenter.DocRefSelectionPresenter.DocRefSelectionView;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DocRefSelectionViewImpl extends ViewImpl implements DocRefSelectionView {

    @UiField
    SimplePanel messageContainer;

    @UiField
    FlowPanel listContainer;

    private final Widget widget;

    @Inject
    public DocRefSelectionViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setDataWidget(final AbstractHasData<?> widget) {
        listContainer.add(widget);
    }

    @Override
    public void setMessage(final String message) {
        messageContainer.clear();
        final HTML messageHtml = new HTML();
        messageHtml.setHTML(SafeHtmlUtil.from(message));
        messageContainer.add(messageHtml);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, DocRefSelectionViewImpl> {

    }
}
