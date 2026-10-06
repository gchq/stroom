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

package stroom.floormap.client.view;

import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.presenter.FloorMapInitPresenter.FloorMapInitView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

/// View implementation for the FloorMap initialisation dialog.
///
/// Provides labelled slots for the Facts Store and Events Store
/// document selection widgets. Uses UiBinder for layout.
public class FloorMapInitViewImpl
        extends ViewImpl
        implements FloorMapInitView {

    private final Widget widget;

    @UiField
    SimplePanel factsStoreContainer;

    @UiField
    SimplePanel eventsStoreContainer;

    /// Creates a new `FloorMapInitViewImpl`.
    ///
    /// @param binder the UiBinder for this view; never null
    @Inject
    public FloorMapInitViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // Both rows hold an injected picker view, not a control of their own, so
        // the FormGroup label has nothing to associate with; name the containers
        // as groups instead. See FloorMapAria.
        FloorMapAria.group(factsStoreContainer, "Facts Store");
        FloorMapAria.group(eventsStoreContainer, "Events Store");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    /// {@inheritDoc}
    @Override
    public void setFactsStoreView(final View view) {
        this.factsStoreContainer.setWidget(view.asWidget());
    }

    /// {@inheritDoc}
    @Override
    public void setEventsStoreView(final View view) {
        this.eventsStoreContainer.setWidget(view.asWidget());
    }

    /// {@inheritDoc}
    ///
    /// Focuses the first control inside the Facts Store row.
    ///
    /// Focusing the container itself does not work: it is a plain
    /// `SimplePanel` div with no tabindex, so `focus()` on it is a
    /// silent no-op and the dialog opens with focus still on whatever the user was
    /// last on — outside the dialog. Reaching for the first real control inside is
    /// what actually moves focus.
    @Override
    public void focus() {
        FloorMapAria.focusFirstFocusable(factsStoreContainer.getElement());
    }

    // --------------------------------------------------------------------------------

    public interface Binder extends UiBinder<Widget, FloorMapInitViewImpl> {

    }
}
