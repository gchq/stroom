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

import stroom.dashboard.shared.LayoutConstraints;

import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;

public class LayoutConstraintPresenter
        extends MyPresenterWidget<LayoutConstraintPresenter.LayoutConstraintView>
        implements HasValueChangeHandlers<LayoutConstraints> {

    @Inject
    public LayoutConstraintPresenter(final EventBus eventBus,
                                     final LayoutConstraintView view) {
        super(eventBus, view);
    }

    public void read(final LayoutConstraints layoutConstraints) {
        if (layoutConstraints != null) {
            getView().setFitWidth(layoutConstraints.isFitWidth());
            getView().setFitHeight(layoutConstraints.isFitHeight());
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<LayoutConstraints> handler) {
        return getView().addValueChangeHandler(handler);
    }

    public interface LayoutConstraintView extends View, Focus, HasValueChangeHandlers<LayoutConstraints> {

        boolean isFitWidth();

        void setFitWidth(boolean fitWidth);

        boolean isFitHeight();

        void setFitHeight(boolean fitHeight);
    }
}

