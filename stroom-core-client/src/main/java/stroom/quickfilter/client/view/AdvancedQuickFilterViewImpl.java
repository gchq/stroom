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

package stroom.quickfilter.client.view;

import stroom.quickfilter.client.presenter.AdvancedQuickFilterPresenter.AdvancedQuickFilterView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class AdvancedQuickFilterViewImpl extends ViewImpl implements AdvancedQuickFilterView, RequiresResize {

    private final Widget widget;

    @UiField
    Label message;
    @UiField
    SimplePanel expression;

    @Inject
    public AdvancedQuickFilterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        message.setVisible(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void onResize() {
        if (widget instanceof final RequiresResize requiresResize) {
            requiresResize.onResize();
        }
    }

    @Override
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
    }

    @Override
    public void setMessage(final String text) {
        message.setText(text == null
                ? ""
                : text);
        message.setVisible(text != null);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, AdvancedQuickFilterViewImpl> {

    }
}
