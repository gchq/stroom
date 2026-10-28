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

import stroom.alert.client.event.AlertEvent;
import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.shared.FloorMapGroup;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/// Modal dialog for editing one [FloorMapGroup] ([FloorMapGroupEdit]) — its name, its highlight
/// colour, and which entities belong to it — shown for one use.
///
/// Membership is edited here rather than inline in the Groups panel because the
/// dock is narrow: a checkbox list with a filter needs the room a popup has. Opened
/// from the panel's New / Edit buttons; on OK it calls back with a replacement
/// group, which the panel persists through the groups bridge.
///
/// **Members the roster has not seen are kept, not dropped.** A
/// group is configuration: an id whose owner has had a quiet afternoon must survive
/// an edit of the group it is in. Those members are listed with an explicit note
/// rather than silently disappearing from the picker.
public class FloorMapGroupEditController extends Controller {

    private final FloorMapGroupEdit view;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus The application's event bus, which the dialog is shown on.
    /// @param view     The dialog's content.
    @Inject
    public FloorMapGroupEditController(final EventBus eventBus,
                                       final FloorMapGroupEdit view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog for the given group, owned by `opener` until it is closed.
    ///
    /// @param opener       What shows it.
    /// @param group        the group to edit; its id is carried through to the
    ///         replacement, so a rename keeps its identity
    /// @param roster       every entity seen on the map, offered as candidate members
    /// @param nameResolver resolves a member id the roster no longer holds to a
    ///         display name; may be `null`
    /// @param isNew        `true` when creating, which only changes the caption
    /// @param onOk         called with the replacement group when the user confirms
    public void show(final HasControllerNode opener,
                     final FloorMapGroup group,
                     final List<EntityEntry> roster,
                     final Function<String, String> nameResolver,
                     final boolean isNew,
                     final Consumer<FloorMapGroup> onOk) {

        view.setName(group.getName());
        view.setColour(group.findColourOrDefault());
        view.setCandidates(candidates(group, roster, nameResolver), group.getMemberIds());

        //noinspection unused e
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(isNew
                        ? "New Group"
                        : "Group — " + group.getName())
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String name = view.getName();
                        if (name == null || name.trim().isEmpty()) {
                            // A nameless row in the panel would be unusable —
                            // there is no other column identifying the group.
                            AlertEvent.fireWarn(this, "Give the group a name.", e::reset);
                            return;
                        }
                        onOk.accept(new FloorMapGroup(
                                group.getId(),
                                name.trim(),
                                view.getColour(),
                                view.getSelectedMemberIds()));
                    }
                    e.hide();
                })
                .fire();
    }

    /// The candidate list the picker shows: every roster entity, plus any current
    /// member the roster does not hold (so it cannot be lost by editing).
    ///
    /// @param group        The group being edited.
    /// @param roster       Every entity seen on the map; may be `null`.
    /// @param nameResolver Resolves a member id the roster does not hold to a display name; may be
    ///                     `null`.
    /// @return The candidates, roster entities first, in order, each id once.
    static List<MemberCandidate> candidates(final FloorMapGroup group,
                                            final List<EntityEntry> roster,
                                            final Function<String, String> nameResolver) {
        final List<MemberCandidate> candidates = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();

        if (roster != null) {
            for (final EntityEntry entry : roster) {
                if (entry != null && entry.getId() != null && seen.add(entry.getId())) {
                    candidates.add(new MemberCandidate(
                            entry.getId(), entry.getDisplayName(), entry.getType(), true));
                }
            }
        }

        for (final String memberId : group.getMemberIds()) {
            if (seen.add(memberId)) {
                final String name = nameResolver != null
                        ? nameResolver.apply(memberId)
                        : null;
                candidates.add(new MemberCandidate(
                        memberId,
                        name != null && !name.isEmpty()
                                ? name
                                : memberId,
                        "",
                        false));
            }
        }
        return candidates;
    }
}
