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

package stroom.streamstore.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.streamstore.client.presenter.IncludeExcludeEntityIdSetPopupPresenter.IncludeExcludeEntityIdSetPopupView;

public class IncludeExcludeEntityIdSetPopupViewImpl extends ViewImpl implements IncludeExcludeEntityIdSetPopupView {
    private final Widget widget;
    @UiField
    FlowPanel includes;
    @UiField
    FlowPanel excludes;
    @Inject
    public IncludeExcludeEntityIdSetPopupViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setIncludesView(final View view) {
        final Widget w = view.asWidget();
        w.setSize("100%", "100%");
        includes.add(w);
    }

    @Override
    public void setExcludesView(final View view) {
        final Widget w = view.asWidget();
        w.setSize("100%", "100%");
        excludes.add(w);
    }

    public interface Binder extends UiBinder<Widget, IncludeExcludeEntityIdSetPopupViewImpl> {
    }
}
