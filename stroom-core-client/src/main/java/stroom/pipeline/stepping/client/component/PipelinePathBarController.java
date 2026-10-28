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

package stroom.pipeline.stepping.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// Shows the menus of the stepping screen's pipeline bar ([PipelinePathBar]): a bar of chevrons
/// showing one path through the pipeline, from its source to an element with no children, with a
/// drop down at each fork to follow another branch. The stepping screen uses the bar as it would
/// the pipeline's tree (its selection, severities, disabled elements and context menu).
///
/// ```java
/// make(pathBarControllerProvider).attach(pipelinePathBar);
/// ```
public class PipelinePathBarController extends Controller {

    /// Made by GIN; [#attach(PipelinePathBar)] gives it its bar.
    ///
    /// @param eventBus The application's event bus, which the menus are shown on.
    @Inject
    public PipelinePathBarController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Attaches this to the bar whose menus it shows.
    ///
    /// @param bar The bar.
    /// @return This, for its owner to keep.
    public PipelinePathBarController attach(final PipelinePathBar bar) {
        Objects.requireNonNull(bar, "bar").setOnShowMenu(this::showMenu);
        return this;
    }

    private void showMenu(final List<Item> items,
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
}
