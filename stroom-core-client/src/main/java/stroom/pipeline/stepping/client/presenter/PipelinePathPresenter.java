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

package stroom.pipeline.stepping.client.presenter;

import stroom.pipeline.structure.client.presenter.PipelineTreePresenter;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// The stepping screen's pipeline: a bar of chevrons showing one path through the pipeline, from
/// its source to an element with no children, with a drop down at each fork to follow another
/// branch. It is a [PipelineTreePresenter] with a different view, so the stepping screen uses it
/// as it would the pipeline's tree (its selection, severities, disabled elements and context
/// menu).
public class PipelinePathPresenter extends PipelineTreePresenter implements PipelinePathUiHandlers {

    @Inject
    public PipelinePathPresenter(final EventBus eventBus, final PipelinePathView view) {
        super(eventBus, view);
        view.setPathUiHandlers(this);
    }

    @Override
    public void onShowMenu(final List<Item> items,
                           final PopupPosition popupPosition,
                           final Element opener,
                           final Item focusItem,
                           final Runnable onHide) {
        ShowMenuEvent.builder()
                .items(items)
                .popupPosition(popupPosition)
                .addAutoHidePartner(opener)
                .focusItem(focusItem)
                .onHide(e -> onHide.run())
                .fire(this);
    }


    // --------------------------------------------------------------------------------


    /// The view of a [PipelinePathPresenter].
    public interface PipelinePathView extends PipelineTreeView {

        /// Sets the handlers of the bar's drop down menus.
        ///
        /// @param uiHandlers The handlers.
        void setPathUiHandlers(PipelinePathUiHandlers uiHandlers);
    }
}
