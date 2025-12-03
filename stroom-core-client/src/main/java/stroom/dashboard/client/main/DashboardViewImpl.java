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

package stroom.dashboard.client.main;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DashboardViewImpl extends ViewImpl
        implements DashboardPresenter.DashboardView {

    private final Widget widget;

    @UiField
    SimplePanel content;

    @Inject
    public DashboardViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setContent(final Widget content) {
        this.content.setWidget(content);
    }

    @Override
    public void setEmbedded(final boolean embedded) {
        content.getElement().getStyle().clearTop();
        if (embedded) {
            content.getElement().getStyle().setTop(0, Unit.PX);
        }
    }

    @Override
    public void setDesignMode(final boolean designMode) {
        if (designMode) {
            widget.addStyleName("dashboard__designMode");
        } else {
            widget.removeStyleName("dashboard__designMode");
        }
    }

    public interface Binder extends UiBinder<Widget, DashboardViewImpl> {

    }
}
