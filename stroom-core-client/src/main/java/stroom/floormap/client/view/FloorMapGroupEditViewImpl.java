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
import stroom.floormap.client.presenter.FloorMapGroupEditPresenter.FloorMapGroupEditView;
import stroom.floormap.client.presenter.FloorMapGroupEditPresenter.MemberCandidate;
import stroom.widget.colour.client.ColourBox;

import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// View implementation for the group edit dialog.
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
public class FloorMapGroupEditViewImpl extends ViewImpl implements FloorMapGroupEditView {

    private static final int ROW_NAME = 0;
    private static final int ROW_COLOUR = 1;

    /// Shown against a member the roster has not seen this session.
    private static final String NOT_ON_MAP = "(not on the map)";

    private final FlowPanel root;
    private final TextBox nameBox = new TextBox();
    private final ColourBox colourBox = new ColourBox();
    private final TextBox filterBox = new TextBox();
    private final FlowPanel memberList = new FlowPanel();
    private final Label summary = new Label();

    /// Checkbox per candidate id, in display order, so selection can be read back.
    private final Map<String, CheckBox> checkBoxesById = new LinkedHashMap<>();

    /// The row widget per candidate id, so the filter can hide and show rows.
    private final Map<String, Widget> rowsById = new LinkedHashMap<>();

    /// Lower-cased searchable text per candidate id, prepared once per show.
    private final Map<String, String> searchTextById = new LinkedHashMap<>();

    @Inject
    public FloorMapGroupEditViewImpl() {
        root = new FlowPanel();
        root.addStyleName("floormap-group-edit");

        final Grid grid = new Grid(2, 2);
        grid.addStyleName("floormap-group-edit-fields");
        grid.setText(ROW_NAME, 0, "Name");
        grid.setWidget(ROW_NAME, 1, nameBox);
        grid.setText(ROW_COLOUR, 0, "Colour");
        grid.setWidget(ROW_COLOUR, 1, colourBox);
        root.add(grid);

        // The Name and Colour labels are <td> text, which names nothing — see
        // FloorMapAria.labelledByCell.
        FloorMapAria.labelledByCell(grid, ROW_NAME, 0, nameBox);
        FloorMapAria.labelledByCell(grid, ROW_COLOUR, 0, colourBox);

        final Label membersLabel = new Label("Members");
        membersLabel.addStyleName("floormap-group-edit-label");
        root.add(membersLabel);

        filterBox.addStyleName("floormap-group-edit-filter");
        filterBox.getElement().setPropertyString("placeholder", "Filter by name, type or id");
        filterBox.addKeyUpHandler(this::onFilterChanged);
        // A placeholder is not an accessible name: it is not exposed by every
        // screen reader and it disappears as soon as anything is typed.
        FloorMapAria.label(filterBox, "Filter members by name, type or id");
        root.add(filterBox);

        memberList.addStyleName("floormap-group-edit-members");
        final ScrollPanel scrollPanel = new ScrollPanel(memberList);
        scrollPanel.addStyleName("floormap-group-edit-scroll");
        // Ties the scrolling tick-list to the "Members" heading above it, so
        // arriving in the list says what the list is.
        final String membersLabelId = FloorMapAria.uniqueId("floormap-members");
        membersLabel.getElement().setId(membersLabelId);
        FloorMapAria.labelledBy(scrollPanel, membersLabelId);
        root.add(scrollPanel);

        summary.addStyleName("floormap-group-edit-summary");
        root.add(summary);
    }

    @Override
    public Widget asWidget() {
        return root;
    }

    @Override
    public void setName(final String name) {
        nameBox.setValue(name != null ? name : "");
    }

    @Override
    public String getName() {
        return nameBox.getValue();
    }

    @Override
    public void setColour(final String colour) {
        colourBox.setValue(colour);
    }

    @Override
    public String getColour() {
        return colourBox.getValue();
    }

    @Override
    public void setCandidates(final List<MemberCandidate> candidates,
                              final List<String> selectedMembers) {
        memberList.clear();
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

        if (checkBoxesById.isEmpty()) {
            final Label empty = new Label(
                    "No entities have been seen on this map yet — run the map's queries first.");
            empty.addStyleName("floormap-group-edit-empty");
            memberList.add(empty);
        }
        updateSummary();
    }

    private void addCandidateRow(final MemberCandidate candidate, final boolean selected) {
        final FlowPanel row = new FlowPanel();
        row.addStyleName("floormap-group-edit-member");

        final CheckBox checkBox = new CheckBox(candidate.getName());
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
            detailLabel.addStyleName("floormap-group-edit-member-detail");
            if (!candidate.isOnTheMap()) {
                detailLabel.addStyleName("floormap-group-edit-member-detail--absent");
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

    @Override
    public List<String> getSelectedMemberIds() {
        // Every row is consulted, including any the filter is currently hiding —
        // an out-of-view tick is still a member.
        final List<String> selected = new ArrayList<>();
        for (final Map.Entry<String, CheckBox> entry : checkBoxesById.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue().getValue())) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    @Override
    public void focus() {
        nameBox.setFocus(true);
        nameBox.selectAll();
    }

    //noinspection unused event
    private void onFilterChanged(final KeyUpEvent event) {
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
}
