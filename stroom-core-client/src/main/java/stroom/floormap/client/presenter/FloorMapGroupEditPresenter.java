/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.client.presenter.FloorMapGroupEditPresenter.FloorMapGroupEditView;
import stroom.floormap.shared.FloorMapGroup;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Modal dialog for editing one {@link FloorMapGroup} — its name, its highlight
 * colour, and which entities belong to it.
 *
 * <p>Membership is edited here rather than inline in the Groups panel because the
 * dock is narrow: a checkbox list with a filter needs the room a popup has. Opened
 * from the panel's New / Edit buttons; on OK it calls back with a replacement
 * group, which the panel persists through the groups bridge.</p>
 *
 * <p><strong>Members the roster has not seen are kept, not dropped.</strong> A
 * group is configuration: an id whose owner has had a quiet afternoon must survive
 * an edit of the group it is in. Those members are listed with an explicit note
 * rather than silently disappearing from the picker.</p>
 */
public class FloorMapGroupEditPresenter extends MyPresenterWidget<FloorMapGroupEditView> {

    @Inject
    public FloorMapGroupEditPresenter(final EventBus eventBus,
                                      final FloorMapGroupEditView view) {
        super(eventBus, view);
    }

    /**
     * Shows the dialog for the given group.
     *
     * @param group        the group to edit; its id is carried through to the
     *                     replacement, so a rename keeps its identity
     * @param roster       every entity seen on the map, offered as candidate members
     * @param nameResolver resolves a member id the roster no longer holds to a
     *                     display name; may be {@code null}
     * @param isNew        {@code true} when creating, which only changes the caption
     * @param onOk         called with the replacement group when the user confirms
     */
    public void show(final FloorMapGroup group,
                     final List<EntityEntry> roster,
                     final Function<String, String> nameResolver,
                     final boolean isNew,
                     final Consumer<FloorMapGroup> onOk) {

        getView().setName(group.getName());
        getView().setColour(group.findColourOrDefault());
        getView().setCandidates(candidates(group, roster, nameResolver), group.getMemberIds());

        //noinspection unused e
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(isNew
                        ? "New Group"
                        : "Group — " + group.getName())
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String name = getView().getName();
                        if (name == null || name.trim().isEmpty()) {
                            // A nameless row in the panel would be unusable —
                            // there is no other column identifying the group.
                            AlertEvent.fireWarn(FloorMapGroupEditPresenter.this,
                                    "Give the group a name.", e::reset);
                            return;
                        }
                        onOk.accept(new FloorMapGroup(
                                group.getId(),
                                name.trim(),
                                getView().getColour(),
                                getView().getSelectedMemberIds()));
                    }
                    e.hide();
                })
                .fire();
    }

    /**
     * The candidate list the picker shows: every roster entity, plus any current
     * member the roster does not hold (so it cannot be lost by editing).
     */
    private static List<MemberCandidate> candidates(final FloorMapGroup group,
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

    /**
     * One row of the member picker.
     *
     * <p>A plain class rather than a record: nothing else in the GWT-compiled
     * source uses records, so this is not the place to find out whether the
     * compiler in use emulates them.</p>
     */
    public static final class MemberCandidate {

        private final String id;
        private final String name;
        private final String type;
        private final boolean onTheMap;

        /**
         * @param id       the entity id — what actually gets stored
         * @param name     the display name
         * @param type     the entity type, shown so a gate and a person are
         *                 distinguishable at a glance; may be empty
         * @param onTheMap {@code false} for a member the roster has not seen this
         *                 session, which the picker flags rather than hiding
         */
        public MemberCandidate(final String id,
                               final String name,
                               final String type,
                               final boolean onTheMap) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.onTheMap = onTheMap;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isOnTheMap() {
            return onTheMap;
        }
    }

    /**
     * View contract: a name field, a colour chooser and a filterable member
     * picker.
     */
    public interface FloorMapGroupEditView extends View {

        void setName(String name);

        String getName();

        void setColour(String colour);

        String getColour();

        /**
         * Populates the member picker.
         *
         * @param candidates      every selectable entity, in display order
         * @param selectedMembers the ids currently in the group
         */
        void setCandidates(List<MemberCandidate> candidates, List<String> selectedMembers);

        /** The ticked member ids, in the picker's display order. */
        List<String> getSelectedMemberIds();

        /** Puts keyboard focus in the name field. */
        void focus();
    }
}
