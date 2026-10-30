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

import stroom.floormap.client.cluster.FloorMapCluster;
import stroom.floormap.client.cluster.FloorMapClusterFilter;
import stroom.floormap.client.cluster.FloorMapClusterMember;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// Lists the members of one cluster ([FloorMapClusterDialog]), in a dialog made for one use, so
/// entities merged into a summary glyph stay reachable.
///
/// This is the part that actually answers the original problem. Ten users at one desk were not
/// merely invisible, they were *unreachable*: nothing on the canvas could be clicked to get at the
/// nine underneath. A count on the glyph says how many there are; the hover tooltip names them;
/// this dialog is where one can be picked out and followed.
///
/// Choosing a row tracks that entity — the same path the Tracking panel uses — and closes the
/// dialog, because the thing the user then wants to look at is the map.
///
/// ## Finding one member
///
/// A crowded map is exactly where clusters get big, and a flat list of several hundred is no more
/// use than the crowd it replaced. A search box and — where the members actually differ — Area and
/// Group dropdowns narrow the list, and rows are ordered by name, since the cluster's own order is
/// an artefact of how the clustering lattice was walked. There is deliberately **no type filter**:
/// clustering runs per type, so every member here shares one. The wording and the matching rules
/// live in the shared, unit-tested [FloorMapClusterFilter].
///
/// The **Area** column has the single meaning it has everywhere else: *which area is this member
/// inside?* It carries no "last seen in" fallback, unlike the Tracking panel's version — this
/// dialog describes one drawn frame and holds no history. Every member of a cluster has a position
/// by construction, so that fallback would have nothing to say.
public class FloorMapClusterDialogController extends Controller {

    /// Room for a dozen or so rows before the grid pages, over the map. Wider than the columns
    /// strictly need so the search box and both dropdowns sit on one line rather than wrapping.
    private static final PopupSize POPUP_SIZE = PopupSize.resizable(700, 500);

    private final FloorMapClusterDialog view;

    /// Every member of the cluster being shown, name-sorted — the list the search and dropdowns
    /// filter. Held because filtering must always run against the whole cluster: narrowing an
    /// already-narrowed list would make the controls one-way, and backspacing in the search box
    /// would never bring rows back.
    private final List<FloorMapClusterMember> allMembers = new ArrayList<>();

    /// Called with a member id when the user picks one; set by [#show].
    private Consumer<String> onTrack;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public FloorMapClusterDialogController(final EventBus eventBus,
                                           final FloorMapClusterDialog view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnFilterChange(this::applyFilter);
        view.setOnTrack(this::trackSelected);
    }

    /// Shows the member list for a cluster in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener       What shows it.
    /// @param cluster      The clicked cluster.
    /// @param nameResolver Resolves a member id to its display name, so a name here matches the
    ///                     name in every grid; may be `null`.
    /// @param membership   The area-containment snapshot backing the Area column; may be `null`.
    /// @param typeOf       Resolves a member id to its entity type; may be `null`, in which case
    ///                     the cluster's own type is used for every row (they are all of one type
    ///                     anyway).
    /// @param groupNamesOf Resolves a member id to the names of the groups it belongs to; may be
    ///                     `null` on a host with no Groups panel, which leaves the Group column
    ///                     empty and the Group filter unoffered.
    /// @param onTrack      Called with the chosen member's id.
    public void show(final HasControllerNode opener,
                     final FloorMapCluster cluster,
                     final Function<String, String> nameResolver,
                     final FloorMapAreaMembership membership,
                     final Function<String, String> typeOf,
                     final Function<String, List<String>> groupNamesOf,
                     final Consumer<String> onTrack) {
        this.onTrack = onTrack;

        final FloorMapAreaMembership areas = membership != null
                ? membership
                : FloorMapAreaMembership.EMPTY;

        final List<FloorMapClusterMember> members = new ArrayList<>(cluster.size());
        for (final String memberId : cluster.getMemberIds()) {
            final String name = nameResolver != null
                    ? nameResolver.apply(memberId)
                    : null;
            final String type = typeOf != null
                    ? typeOf.apply(memberId)
                    : null;
            final List<String> groups = groupNamesOf != null
                    ? groupNamesOf.apply(memberId)
                    : null;
            members.add(new FloorMapClusterMember(
                    memberId,
                    name != null && !name.isEmpty()
                            ? name
                            : memberId,
                    type != null && !type.isEmpty()
                            ? type
                            : cluster.getType(),
                    areaNamesFor(memberId, areas, nameResolver),
                    groups));
        }

        allMembers.clear();
        allMembers.addAll(FloorMapClusterFilter.sortedByName(members));

        view.clearFilters();
        view.setAreaFilterOptions(FloorMapClusterFilter.areaOptions(allMembers));
        view.setGroupFilterOptions(FloorMapClusterFilter.groupOptions(allMembers));
        applyFilter();
        view.clearSelection();

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(POPUP_SIZE)
                // Says what the dialog is a list OF, not just a count.
                .caption(cluster.getLabel() + " in this cluster")
                // Typing is the most likely next action in a dialog opened to find someone, so the
                // search box takes the caret.
                .onShow(e -> view.focusSearch())
                .fire();
    }

    // Re-runs the search and the dropdowns over the whole cluster and shows what survives
    private void applyFilter() {
        view.setMembers(FloorMapClusterFilter.filter(
                allMembers,
                view.getSearchText(),
                view.getAreaFilter(),
                view.getGroupFilter()));
    }

    // Tracks the selected member and closes, since the map is what to look at next
    private void trackSelected() {
        final FloorMapClusterMember selected = view.getSelected();
        if (selected != null && onTrack != null) {
            onTrack.accept(selected.getId());
            ControllerDialog.pressAction(this, DialogAction.CLOSE);
        }
    }

    /// The names of every area containing a member, innermost (most specific) first — the same
    /// order and the same joining the Tracking panel's Area column uses, so the two never read
    /// differently for one entity.
    private static List<String> areaNamesFor(final String memberId,
                                             final FloorMapAreaMembership membership,
                                             final Function<String, String> nameResolver) {
        final List<String> keys = membership.getAreaKeys(memberId);
        final List<String> names = new ArrayList<>(keys.size());
        for (final String key : keys) {
            final String name = nameResolver != null
                    ? nameResolver.apply(key)
                    : null;
            names.add(name != null && !name.isEmpty()
                    ? name
                    : key);
        }
        return names;
    }
}
