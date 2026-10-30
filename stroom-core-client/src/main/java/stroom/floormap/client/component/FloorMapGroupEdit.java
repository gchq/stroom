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

import stroom.floormap.client.model.MemberCandidate;
import stroom.widget.colour.client.ColourBox;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.util.client.AriaUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The group edit dialog's content, which its [FloorMapGroupEditController] shows.
///
/// ```
/// Name      [ Maintenance        ]
/// Colour    [ ■ ]
///
/// Members
/// [ filter…                      ]
/// ┌──────────────────────────────┐
/// │ ☑ bob        person          │
/// │ ☐ Gate 3     gate            │
/// │ ☑ sue        (not on the map) │
/// └──────────────────────────────┘
/// ```
///
/// The filter box hides non-matching rows; it deliberately does **not**
/// clear their ticks, so filtering can never silently drop a member the user cannot
/// see. Selection is read from the checkbox state of every row, hidden or not.
public class FloorMapGroupEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Shown against a member the roster has not seen this session.
    private static final String NOT_ON_MAP = "(not on the map)";

    @UiField
    TableCellElement nameLabel;
    @UiField
    TextBox nameBox;
    @UiField
    TableCellElement colourLabel;
    @UiField
    ColourBox colourBox;
    @UiField
    Label membersLabel;
    @UiField
    TextBox filterBox;
    @UiField
    ScrollPanel memberScroll;
    @UiField
    FlowPanel memberList;
    @UiField
    Label empty;
    @UiField
    Label summary;

    /// Checkbox per candidate id, in display order, so selection can be read back.
    private final Map<String, TickBox> checkBoxesById = new LinkedHashMap<>();

    /// The row widget per candidate id, so the filter can hide and show rows.
    private final Map<String, Widget> rowsById = new LinkedHashMap<>();

    /// Lower-cased searchable text per candidate id, prepared once per show.
    private final Map<String, String> searchTextById = new LinkedHashMap<>();

    /// Makes the dialog's content.
    public FloorMapGroupEdit() {
        initWidget(BINDER.createAndBindUi(this));

        // The Name and Colour labels are <td> text, which names nothing on its own
        nameLabel.setId(AriaUtil.uniqueId("floormap-label"));
        AriaUtil.labelledBy(nameBox, nameLabel.getId());
        colourLabel.setId(AriaUtil.uniqueId("floormap-label"));
        AriaUtil.labelledBy(colourBox, colourLabel.getId());

        //noinspection unused e
        filterBox.addKeyUpHandler(e -> onFilterChanged());

        // Ties the scrolling tick-list to the "Members" heading above it, so arriving in the list
        // says what the list is
        final String membersLabelId = AriaUtil.uniqueId("floormap-members");
        membersLabel.getElement().setId(membersLabelId);
        AriaUtil.labelledBy(memberScroll, membersLabelId);
    }

    /// @param name The group's name.
    public void setName(final String name) {
        nameBox.setValue(name != null
                ? name
                : "");
    }

    /// @return The group's name, as typed.
    public String getName() {
        return nameBox.getValue();
    }

    /// @param colour The group's highlight colour.
    public void setColour(final String colour) {
        colourBox.setValue(colour);
    }

    /// @return The group's highlight colour.
    public String getColour() {
        return colourBox.getValue();
    }

    /// Fills the member picker, clearing the filter.
    ///
    /// @param candidates      Every selectable entity, in display order.
    /// @param selectedMembers The ids currently in the group.
    public void setCandidates(final List<MemberCandidate> candidates,
                              final List<String> selectedMembers) {
        for (final Widget row : rowsById.values()) {
            row.removeFromParent();
        }
        checkBoxesById.clear();
        rowsById.clear();
        searchTextById.clear();
        filterBox.setValue("");

        if (candidates != null) {
            for (final MemberCandidate candidate : candidates) {
                addCandidateRow(candidate,
                        selectedMembers != null && selectedMembers.contains(candidate.getId()));
            }
        }

        empty.setVisible(checkBoxesById.isEmpty());
        updateSummary();
    }

    /// @return The ticked member ids, in the picker's display order.
    public List<String> getSelectedMemberIds() {
        // Every row is consulted, including any the filter is currently hiding —
        // an out-of-view tick is still a member.
        final List<String> selected = new ArrayList<>();
        for (final Map.Entry<String, TickBox> entry : checkBoxesById.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue().getValue())) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    /// Puts the focus in the name field, with its text selected.
    public void focus() {
        nameBox.setFocus(true);
        nameBox.selectAll();
    }

    private void addCandidateRow(final MemberCandidate candidate, final boolean selected) {
        final FlowPanel row = new FlowPanel();
        row.addStyleName("floor-map-group-edit__member");

        final TickBox checkBox = new TickBox();
        checkBox.addStyleName("floor-map-group-edit__member-tick-box");
        checkBox.setLabel(candidate.getName());
        checkBox.setValue(selected);
        //noinspection unused e
        checkBox.addValueChangeHandler(e -> updateSummary());
        // The id is the thing actually stored, so make it visible on hover rather
        // than leaving the user to guess which "bob" a row means.
        checkBox.setTitle(candidate.getId());
        row.add(checkBox);

        final String detail = candidate.isOnTheMap()
                ? candidate.getType()
                : NOT_ON_MAP;
        if (detail != null && !detail.isEmpty()) {
            final Label detailLabel = new Label(detail);
            detailLabel.addStyleName("floor-map-group-edit__member-detail");
            if (!candidate.isOnTheMap()) {
                detailLabel.addStyleName("floor-map-group-edit__member-detail--absent");
                detailLabel.setTitle("This member has not been seen on the map this session."
                                     + " It stays in the group.");
            }
            row.add(detailLabel);
        }

        memberList.add(row);
        checkBoxesById.put(candidate.getId(), checkBox);
        rowsById.put(candidate.getId(), row);
        searchTextById.put(candidate.getId(),
                (candidate.getName() + " " + candidate.getType() + " " + candidate.getId())
                        .toLowerCase());
    }

    private void onFilterChanged() {
        final String filter = filterBox.getValue() != null
                ? filterBox.getValue().trim().toLowerCase()
                : "";
        for (final Map.Entry<String, Widget> entry : rowsById.entrySet()) {
            final String searchText = searchTextById.get(entry.getKey());
            entry.getValue().setVisible(filter.isEmpty()
                                        || (searchText != null && searchText.contains(filter)));
        }
    }

    /// Spells out how many members are ticked, so the count is visible without
    /// counting rows — and so a selection hidden by the filter is still accounted
    /// for.
    private void updateSummary() {
        final int selected = getSelectedMemberIds().size();
        final int total = checkBoxesById.size();
        summary.setText(selected == 1
                ? "1 member selected of " + total + " entities"
                : selected + " members selected of " + total + " entities");
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapGroupEdit> {

    }
}
