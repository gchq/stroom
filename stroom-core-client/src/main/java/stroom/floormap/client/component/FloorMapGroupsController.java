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
import stroom.floormap.client.overlay.FloorMapGroupSnapshot;
import stroom.floormap.shared.FloorMapGroup;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/// The floor map's Groups panel ([FloorMapGroups]): the document's groups as edited, which of them
/// are highlighted on the map, and their live counts. New Group and Edit Group open the group
/// dialog; its owner is told of every edit (to stage it for save) and every change to what is
/// highlighted (to draw the overlay).
public class FloorMapGroupsController extends Controller {

    private final FloorMapGroups view;
    private final Provider<FloorMapGroupEditController> groupEditProvider;

    // The document's groups as last read or edited, in display order
    private List<FloorMapGroup> groups = new ArrayList<>();
    // The groups highlighted on the canvas, by id (so a rename keeps a highlight): the view's state,
    // empty for each document and never saved
    private final Set<String> shownGroupIds = new LinkedHashSet<>();
    private FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.EMPTY;
    // The roster the member picker offers, from the owner
    private List<EntityEntry> roster = Collections.emptyList();
    private Function<String, String> nameResolver;
    private Consumer<List<FloorMapGroup>> onGroupsEdit = edited -> {
    };
    private Runnable onHighlightChange = () -> {
    };
    // New groups' ids
    private final Random random = new Random();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param groupEditProvider Makes the group dialog.
    @Inject
    public FloorMapGroupsController(final EventBus eventBus,
                                    final FloorMapGroups view,
                                    final Provider<FloorMapGroupEditController> groupEditProvider) {
        super(eventBus);
        this.view = view;
        this.groupEditProvider = groupEditProvider;
        initWidget(view);
        view.setShownGroupIds(shownGroupIds);
        view.setOnNew(() -> editNewGroup(FloorMapGroup.create(groups, random)));
        view.setOnEdit(this::onEdit);
        view.setOnDelete(this::onDelete);
        view.setOnToggleHighlight(this::toggleHighlight);
        // The groups are the document's, so they can't be changed while it is read only (a group's
        // highlight, which is only the view's, still can)
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Sets the groups read from the document. The highlights and selection are kept, as saving
    /// reads every visited tab again (clearing them would switch the user's highlights off at each
    /// save); only those of groups gone are dropped. The counts are kept too: the queries that
    /// follow refresh them, and zeroing them would flash "0 of N" on every row at each save.
    ///
    /// @param groups The document's groups; null for none.
    public void setGroups(final List<FloorMapGroup> groups) {
        this.groups = groups != null
                ? new ArrayList<>(groups)
                : new ArrayList<>();
        shownGroupIds.retainAll(groupIds());
        view.setGroups(this.groups);
        onHighlightChange.run();
    }

    /// Sets the live counts: called at each query refresh (~300ms during playback), so the rows
    /// are drawn again only when the counts have changed.
    ///
    /// @param snapshot The counts; null for none.
    public void setSnapshot(final FloorMapGroupSnapshot snapshot) {
        final FloorMapGroupSnapshot next = snapshot != null
                ? snapshot
                : FloorMapGroupSnapshot.EMPTY;
        final boolean changed = !Objects.equals(this.snapshot, next);
        this.snapshot = next;
        view.setSnapshot(next);
        if (changed) {
            view.redraw();
        }
    }

    /// Sets the entities the member picker offers, and how members and areas are named.
    ///
    /// @param roster       Every entity seen on the map; null for none.
    /// @param nameResolver Names an entity by its id; null to show ids.
    public void setRoster(final List<EntityEntry> roster, final Function<String, String> nameResolver) {
        this.roster = roster != null
                ? roster
                : Collections.emptyList();
        this.nameResolver = nameResolver;
        view.setNameResolver(nameResolver);
        // Names come from the resolver, so a new roster can change them (an area learning its label)
        view.redraw();
    }

    /// Adds an entity to a group, from elsewhere (the Tracking panel's Add to Group); nothing
    /// happens if it is already a member.
    ///
    /// @param groupId  The group's id.
    /// @param entityId The entity.
    public void addMember(final String groupId, final String entityId) {
        final FloorMapGroup group = FloorMapGroup.find(groups, groupId);
        if (group == null || entityId == null || group.contains(entityId)) {
            return;
        }
        applyEdit(FloorMapGroup.replace(groups, group.withMember(entityId)));
    }

    /// Makes a group with just this entity (the Tracking panel's New Group with this entity) and
    /// opens the group dialog on it: the caller is on another tab, and highlights start off, so a
    /// group made silently would show nothing; and it is named when the user cares about it.
    ///
    /// @param entityId The first member, or null for none.
    public void createGroupWith(final String entityId) {
        editNewGroup(FloorMapGroup.create(groups, random).withMember(entityId));
    }

    /// @return The groups as edited, e.g. for an "add to…" menu.
    public List<FloorMapGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    /// @return The ids of the groups highlighted, for the canvas's overlay.
    public Set<String> getShownGroupIds() {
        return Collections.unmodifiableSet(shownGroupIds);
    }

    /// @param onGroupsEdit Given the groups each time the user edits them, to stage them for save.
    public void setOnGroupsEdit(final Consumer<List<FloorMapGroup>> onGroupsEdit) {
        this.onGroupsEdit = onGroupsEdit;
    }

    /// @param onHighlightChange Run each time what is highlighted changes, to draw the overlay again.
    public void setOnHighlightChange(final Runnable onHighlightChange) {
        this.onHighlightChange = onHighlightChange;
    }

    // A group not yet added, added and selected only on OK: Cancel leaves no empty group behind
    private void editNewGroup(final FloorMapGroup created) {
        groupEditProvider.get().show(this, created, roster, nameResolver, true, saved -> {
            applyEdit(FloorMapGroup.replace(groups, saved));
            view.select(saved);
        });
    }

    private void onEdit(final FloorMapGroup selected) {
        groupEditProvider.get().show(this, selected, roster, nameResolver, false, saved ->
                applyEdit(FloorMapGroup.replace(groups, saved)));
    }

    private void onDelete(final FloorMapGroup selected) {
        // Highlights are by id, so the group's goes with it, or a later group with its id would
        // have it
        shownGroupIds.remove(selected.getId());
        view.clearSelection();
        applyEdit(FloorMapGroup.without(groups, selected.getId()));
        onHighlightChange.run();
    }

    private void toggleHighlight(final FloorMapGroup group) {
        if (!shownGroupIds.remove(group.getId())) {
            shownGroupIds.add(group.getId());
        }
        view.redraw();
        onHighlightChange.run();
    }

    // Shows the edited groups and tells the owner, to stage them (which enables Save)
    private void applyEdit(final List<FloorMapGroup> edited) {
        groups = new ArrayList<>(edited);
        // Only groups that still exist can be highlighted
        shownGroupIds.retainAll(groupIds());
        view.setGroups(groups);
        onGroupsEdit.accept(new ArrayList<>(groups));
        // A membership change changes what is highlighted, if the group is
        onHighlightChange.run();
    }

    private Set<String> groupIds() {
        final Set<String> ids = new LinkedHashSet<>();
        for (final FloorMapGroup group : groups) {
            ids.add(group.getId());
        }
        return ids;
    }
}
