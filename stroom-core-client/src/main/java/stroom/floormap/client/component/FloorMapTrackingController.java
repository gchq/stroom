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

package stroom.floormap.client.component;

import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.floormap.shared.FloorMapGroup;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// The floor map's Tracking panel ([FloorMapTracking]): its owner gives it the entities and where
/// they are, and is told which is tracked. Add to Group offers the owner's groups in a menu.
public class FloorMapTrackingController extends Controller {

    private final FloorMapTracking view;

    private Supplier<List<FloorMapGroup>> groupsSupplier;
    private BiConsumer<String, String> addToGroup;
    private Consumer<String> createGroupWith;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus, which shows menus.
    /// @param view     The content.
    @Inject
    public FloorMapTrackingController(final EventBus eventBus, final FloorMapTracking view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnAddToGroup(this::showAddToGroupMenu);
    }

    /// Offers Add to Group; without it the button stays disabled, so a host with no Groups panel
    /// (the Editor tab) doesn't offer it.
    ///
    /// @param groupsSupplier  Supplies the groups, in order.
    /// @param addToGroup      Adds an entity (second) to a group (first, by id).
    /// @param createGroupWith Makes a group with the entity in it.
    public void setAddToGroupSupport(final Supplier<List<FloorMapGroup>> groupsSupplier,
                                     final BiConsumer<String, String> addToGroup,
                                     final Consumer<String> createGroupWith) {
        this.groupsSupplier = groupsSupplier;
        this.addToGroup = addToGroup;
        this.createGroupWith = createGroupWith;
        view.setAddToGroupAvailable(groupsSupplier != null);
    }

    /// @return The grid's element id, for the canvas to point `aria-describedby` at.
    public String getGridElementId() {
        return view.getGridElementId();
    }

    /// Shows the entities in place of those shown; the selection is left as it is.
    ///
    /// @param entities The entities.
    public void setEntities(final List<EntityEntry> entities) {
        view.setEntities(entities);
    }

    /// Selects the entity with this id to track, or none.
    ///
    /// @param id The id, or null for none.
    public void setSelected(final String id) {
        view.setSelected(id);
    }

    /// @return The id of the entity tracked, or null if none is.
    public String getSelectedId() {
        return view.getSelectedId();
    }

    /// Sets which areas each entity is in now.
    ///
    /// @param areaMembership Which areas each entity is in; null for none.
    /// @param nameResolver   Names an entity by its id; null to show ids.
    public void setAreaMembership(final FloorMapAreaMembership areaMembership,
                                  final Function<String, String> nameResolver) {
        view.setAreaMembership(areaMembership, nameResolver);
    }

    /// Forgets which areas entities are and were in, for a document opened (again).
    public void clearAreaState() {
        view.clearAreaState();
    }

    /// @param onSelectionChange Given the entity to track, or null to stop, each time it changes,
    ///                          and again when the selected row is clicked.
    public void setOnSelectionChange(final Consumer<EntityEntry> onSelectionChange) {
        view.setOnSelectionChange(onSelectionChange);
    }

    // Every group, then New group with the entity. A group it is already in is ticked and
    // disabled, not hidden, which would leave the user wondering whether the group still exists
    private void showAddToGroupMenu(final EntityEntry selected) {
        if (groupsSupplier == null) {
            return;
        }
        final String entityId = selected.getId();
        final List<FloorMapGroup> groups = groupsSupplier.get();
        final List<Item> items = new ArrayList<>();
        int priority = 0;
        if (groups != null) {
            for (final FloorMapGroup group : groups) {
                final boolean alreadyIn = group.contains(entityId);
                final IconMenuItem.Builder builder = new IconMenuItem.Builder()
                        .priority(priority++)
                        // The count tells a populated group from an empty one before adding to it
                        .text(group.getName() + " (" + group.countMembers()
                              + (group.countMembers() == 1
                                ? " member)"
                                : " members)"))
                        .enabled(!alreadyIn);
                if (alreadyIn) {
                    builder.icon(SvgImage.TICK)
                            .tooltip(selected.getDisplayName() + " is already in this group");
                } else {
                    builder.command(() -> addToGroup.accept(group.getId(), entityId));
                }
                items.add(builder.build());
            }
            if (!groups.isEmpty()) {
                items.add(new Separator(priority++));
            }
        }
        items.add(new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.ADD)
                .text("New group with " + selected.getDisplayName())
                .command(() -> createGroupWith.accept(entityId))
                .build());
        ShowMenuEvent.builder()
                .name("Add '" + selected.getDisplayName() + "' to Group")
                .items(items)
                .popupPosition(new PopupPosition(new Rect(view.getAddToGroupElement()).grow(3),
                        PopupLocation.BELOW))
                .fire(this);
    }
}
