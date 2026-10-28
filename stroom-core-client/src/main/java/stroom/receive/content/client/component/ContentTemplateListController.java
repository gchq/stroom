/*
 * Copyright 2025 Crown Copyright
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

package stroom.receive.content.client.component;

import stroom.data.client.component.DocRefCell;
import stroom.data.client.component.RefCellsController;
import stroom.receive.content.shared.ContentTemplate;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// The content templates ([ContentTemplateList]), as its owner gives them; a template's action menu
/// has the items its owner gives.
public class ContentTemplateListController extends Controller implements Focus {

    private final ContentTemplateList view;
    private Function<ContentTemplate, List<Item>> actionMenuItemProvider = template -> List.of();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    /// @param refCells Lets the pipeline cells open their pipelines.
    @Inject
    public ContentTemplateListController(final EventBus eventBus,
                                         final ContentTemplateList view,
                                         final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        own(refCells);
        initWidget(view);
        view.addColumns(refCells.configure(new DocRefCell.Builder<ContentTemplate>()));
        view.setOnShowActionMenu((row, popupPosition) -> ShowMenuEvent
                .builder()
                .name(ContentTemplateList.getActionMenuName(row))
                .items(actionMenuItemProvider.apply(row))
                .popupPosition(popupPosition)
                .fire(this));
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// @param data The templates.
    public void setData(final List<ContentTemplate> data) {
        view.setData(data);
    }

    /// @return The selection, one template at most.
    public MultiSelectionModel<ContentTemplate> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// @param onEnabledChange Given a template and whether it is now enabled.
    public void setOnEnabledChange(final BiConsumer<ContentTemplate, Boolean> onEnabledChange) {
        view.setOnEnabledChange(onEnabledChange);
    }

    /// @param actionMenuItemProvider Gives a template's action menu.
    public void setActionMenuItemProvider(final Function<ContentTemplate, List<Item>> actionMenuItemProvider) {
        this.actionMenuItemProvider = actionMenuItemProvider;
    }
}
