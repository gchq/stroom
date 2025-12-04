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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.AnalyticStreamDestinationPresenter.AnalyticStreamDestinationView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnalyticStreamDestinationViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AnalyticStreamDestinationView {

    private final Widget widget;

    @UiField
    SimplePanel destinationFeed;
    @UiField
    CustomCheckBox useSourceFeedIfPossible;
    @UiField
    CustomCheckBox includeRuleDocumentation;

    @Inject
    public AnalyticStreamDestinationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setDestinationFeedView(final View view) {
        this.destinationFeed.setWidget(view.asWidget());
    }

    @Override
    public boolean isUseSourceFeedIfPossible() {
        return this.useSourceFeedIfPossible.getValue();
    }

    public boolean isIncludeRuleDocumentation() {
        return this.includeRuleDocumentation.getValue();
    }

    @Override
    public void setUseSourceFeedIfPossible(final boolean useSourceFeedIfPossible) {
        this.useSourceFeedIfPossible.setValue(useSourceFeedIfPossible);
    }

    public void setIncludeRuleDocumentation(final boolean includeRuleDocumentation) {
        this.includeRuleDocumentation.setValue(includeRuleDocumentation);
    }

    @UiHandler("useSourceFeedIfPossible")
    public void onUseSourceFeedIfPossible(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("includeRuleDocumentation")
    public void onIncludeRuleDocumentation(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    public interface Binder extends UiBinder<Widget, AnalyticStreamDestinationViewImpl> {

    }
}
