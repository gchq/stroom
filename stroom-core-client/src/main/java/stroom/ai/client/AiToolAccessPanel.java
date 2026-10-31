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

package stroom.ai.client;

import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolCategoryGrant;
import stroom.ai.shared.AiToolEffectiveGrant;
import stroom.ai.shared.AiToolSummary;
import stroom.cell.expander.client.ExpanderCell;
import stroom.data.grid.client.MyDataGrid;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A panel showing the tool access some grants give: a heading row above a grid of every category, each a row
/// that opens to its tools, with whether its calls that read, and those that write, are permitted, and what it does.
/// A category not granted is greyed out, with none of its tools permitted.
///
/// For a policy ([#forPolicy(HasHandlers)]) the heading row has a tool search, which shows only the matching
/// tools, highlighted, in their categories, opened. For a user's effective access
/// ([#forEffectiveAccess(HasHandlers)]) the grid also says which policies each grant comes from.
public class AiToolAccessPanel extends Composite {

    private final FlowPanel root = new FlowPanel();
    private final FlowPanel headingPanel = new FlowPanel();
    private final Label heading = new Label();
    private final MyDataGrid<Row> dataGrid;
    // The categories opened, kept as the user left them when other grants are shown, and when a search is cleared.
    private final Set<AiToolCategory> opened = EnumSet.noneOf(AiToolCategory.class);
    // The categories opened while searching.
    private final Set<AiToolCategory> openedWhileSearching = EnumSet.noneOf(AiToolCategory.class);

    // The access each granted category has, or null if there is nothing to show.
    private Map<AiToolCategory, AiToolAccess> access;
    // The policies each category's access comes from, if shown.
    private Map<AiToolCategory, AiToolEffectiveGrant> effectiveGrants;
    // Stroom's tools, or null until they have been fetched.
    private List<AiToolSummary> tools;
    private String query = "";

    private AiToolAccessPanel(final HasHandlers eventBus, final boolean withSearch, final boolean withGrantedBy) {
        headingPanel.setStyleName("dock-min aiToolPolicies-heading");
        heading.setStyleName("aiToolPolicies-headingText");
        headingPanel.add(heading);
        if (withSearch) {
            headingPanel.add(toolSearch());
        }

        dataGrid = new MyDataGrid<>(eventBus);
        dataGrid.setTableName("MCP Tool Access");
        initColumns(withGrantedBy);

        final FlowPanel gridPanel = new FlowPanel();
        gridPanel.setStyleName("dock-max");
        gridPanel.add(dataGrid);

        root.setStyleName("max dock-container-vertical");
        root.add(headingPanel);
        root.add(gridPanel);
        initWidget(root);
        render();
    }

    /// @param eventBus The event bus, for the grid.
    /// @return A panel for the categories a policy grants, with a tool search.
    public static AiToolAccessPanel forPolicy(final HasHandlers eventBus) {
        return new AiToolAccessPanel(eventBus, true, false);
    }

    /// @param eventBus The event bus, for the grid.
    /// @return A panel for a user's effective tool access, saying which policies it comes from.
    public static AiToolAccessPanel forEffectiveAccess(final HasHandlers eventBus) {
        return new AiToolAccessPanel(eventBus, false, true);
    }

    private QuickFilter toolSearch() {
        final QuickFilter toolSearch = new QuickFilter();
        toolSearch.addStyleName("aiToolAccess-search");
        final NodeList<Element> inputs = toolSearch.getElement().getElementsByTagName("input");
        if (inputs.getLength() > 0) {
            inputs.getItem(0).setAttribute("placeholder", "Search tools");
        }
        toolSearch.registerPopupTextProvider(() -> new SafeHtmlBuilder()
                .appendHtmlConstant("<b>Search tools</b><br>")
                .appendEscaped("Shows only the tools whose name contains the text, highlighted, in their "
                               + "categories.")
                .toSafeHtml());
        // The search's own change event comes once typing pauses; the key handler searches as each key is typed.
        toolSearch.addValueChangeHandler(event -> search(toolSearch.getText()));
        toolSearch.addKeyDownHandler(event -> Scheduler.get().scheduleDeferred(() -> search(toolSearch.getText())));
        return toolSearch;
    }

    private void initColumns(final boolean withGrantedBy) {
        final Column<Row, Expander> expanderColumn = new Column<Row, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final Row row) {
                return row.tool() == null
                        ? new Expander(0, openNow().contains(row.category()), false)
                        : new Expander(0, false, true);
            }
        };
        expanderColumn.setFieldUpdater((index, row, value) -> {
            if (row.tool() == null) {
                final Set<AiToolCategory> openNow = openNow();
                if (!openNow.remove(row.category())) {
                    openNow.add(row.category());
                }
                render();
            }
        });
        dataGrid.addColumn(expanderColumn, "", ExpanderCell.getColumnWidth(0));

        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(this::toolCell)
                        .enabledWhen(this::isGranted)
                        .build(),
                DataGridUtil.headingBuilder("Tool")
                        .withToolTip("The category, with the access it is granted, or the tool.")
                        .build(),
                260);
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, (Row row) -> tickOrCross(canRead(row), "read"))
                        .centerAligned()
                        .withConditionalStyleName(row -> deniedStyle(canRead(row)))
                        .enabledWhen(this::isGranted)
                        .build(),
                DataGridUtil.headingBuilder("Read")
                        .centerAligned()
                        .withToolTip("Whether the calls that cannot change anything are permitted. Blank for a tool "
                                     + "with none.")
                        .build(),
                60);
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, (Row row) -> tickOrCross(canWrite(row), "write"))
                        .centerAligned()
                        .withConditionalStyleName(row -> deniedStyle(canWrite(row)))
                        .enabledWhen(this::isGranted)
                        .build(),
                DataGridUtil.headingBuilder("Write")
                        .centerAligned()
                        .withToolTip("Whether the calls that change things are permitted, which needs read and "
                                     + "write. Blank for a tool that cannot change anything.")
                        .build(),
                60);
        if (withGrantedBy) {
            dataGrid.addResizableColumn(
                    DataGridUtil.textWithTooltipColumnBuilder(this::grantedBy)
                            .enabledWhen(this::isGranted)
                            .build(),
                    DataGridUtil.headingBuilder("Granted By")
                            .withToolTip("The policies the access comes from.")
                            .build(),
                    220);
        }
        dataGrid.addAutoResizableColumn(
                DataGridUtil.htmlColumnBuilder(this::descriptionCell)
                        .enabledWhen(this::isGranted)
                        .build(),
                DataGridUtil.headingBuilder("Description")
                        .withToolTip("What the category holds, or what the tool does.")
                        .build(),
                400);
    }

    /// @param widget Put in a row of its own below the heading row.
    public void addRow(final Widget widget) {
        widget.addStyleName("dock-min");
        root.insert(widget, 1);
    }

    /// @param text What the panel is showing, or null for no heading row (if it has no tool search).
    public void setHeading(final String text) {
        heading.setText(NullSafe.string(text));
        headingPanel.setVisible(text != null || headingPanel.getWidgetCount() > 1);
    }

    /// @param grants The categories granted, with their access, or null to show nothing.
    public void setGrants(final List<AiToolCategoryGrant> grants) {
        effectiveGrants = null;
        if (grants == null) {
            access = null;
        } else {
            access = new EnumMap<>(AiToolCategory.class);
            for (final AiToolCategoryGrant grant : grants) {
                if (grant.getCategory() != null) {
                    access.merge(grant.getCategory(), grant.getAccess(), AiToolAccess::or);
                }
            }
        }
        render();
    }

    /// @param grants The categories granted, with the policies they come from, or null to show nothing.
    public void setEffectiveGrants(final List<AiToolEffectiveGrant> grants) {
        if (grants == null) {
            access = null;
            effectiveGrants = null;
        } else {
            access = new EnumMap<>(AiToolCategory.class);
            effectiveGrants = new EnumMap<>(AiToolCategory.class);
            for (final AiToolEffectiveGrant grant : grants) {
                if (grant.getCategory() != null) {
                    access.put(grant.getCategory(), grant.getAccess());
                    effectiveGrants.put(grant.getCategory(), grant);
                }
            }
        }
        render();
    }

    /// @param tools Stroom's tools, with their categories.
    public void setTools(final List<AiToolSummary> tools) {
        this.tools = tools;
        search(query, true);
    }

    private void search(final String text) {
        search(text, false);
    }

    // Opens every category with a matching tool, as the search is typed. Clearing the search leaves the categories
    // as they were before it.
    private void search(final String text, final boolean force) {
        final String trimmed = NullSafe.trim(text);
        if (!force && Objects.equals(trimmed, query)) {
            return;
        }
        query = trimmed;
        openedWhileSearching.clear();
        for (final AiToolCategory category : AiToolCategory.values()) {
            if (!matchingTools(category).isEmpty()) {
                openedWhileSearching.add(category);
            }
        }
        render();
    }

    private boolean isSearching() {
        return !query.isEmpty();
    }

    private Set<AiToolCategory> openNow() {
        return isSearching()
                ? openedWhileSearching
                : opened;
    }

    // The category's tools, or, while searching, only those that match.
    private List<AiToolSummary> matchingTools(final AiToolCategory category) {
        final List<AiToolSummary> categoryTools = AiToolPolicyText.toolsIn(category, tools);
        if (!isSearching()) {
            return categoryTools;
        }
        return categoryTools.stream()
                .filter(tool -> AiToolPolicyText.matches(tool, query))
                .toList();
    }

    private void render() {
        final List<Row> rows = new ArrayList<>();
        if (access != null) {
            for (final AiToolCategory category : AiToolCategory.values()) {
                final List<AiToolSummary> categoryTools = matchingTools(category);
                // While searching, a category with no matching tool is left out.
                if (!isSearching() || !categoryTools.isEmpty()) {
                    rows.add(new Row(category, null));
                    if (openNow().contains(category)) {
                        for (final AiToolSummary tool : categoryTools) {
                            rows.add(new Row(category, tool));
                        }
                    }
                }
            }
        }
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
    }

    private boolean isGranted(final Row row) {
        return access != null && access.containsKey(row.category());
    }

    private AiToolAccess accessTo(final Row row) {
        return access == null
                ? null
                : access.get(row.category());
    }

    private SafeHtml toolCell(final Row row) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (row.tool() == null) {
            final AiToolAccess categoryAccess = accessTo(row);
            builder.appendHtmlConstant("<span class=\"aiToolAccess-category\">");
            builder.appendEscaped(row.category().getDisplayValue());
            builder.appendHtmlConstant("</span> <span class=\"aiToolAccess-access\">");
            builder.appendEscaped(categoryAccess == null
                    ? "(not granted)"
                    : "(" + categoryAccess.getDisplayValue() + ")");
            builder.appendHtmlConstant("</span>");
        } else {
            builder.appendHtmlConstant("<span class=\"aiToolAccess-tool\">");
            builder.append(AiToolPolicyText.highlight(row.tool().getName(), query));
            builder.appendHtmlConstant("</span>");
        }
        return builder.toSafeHtml();
    }

    private Boolean canRead(final Row row) {
        return AiToolPolicyText.canRead(row.tool(), accessTo(row));
    }

    private Boolean canWrite(final Row row) {
        return AiToolPolicyText.canWrite(row.tool(), accessTo(row));
    }

    // Shows a cross in red.
    private static String deniedStyle(final Boolean permitted) {
        return Boolean.FALSE.equals(permitted)
                ? "aiToolAccess-denied"
                : "";
    }

    // A tick if permitted, a cross if not, or nothing if there is nothing of that kind.
    private static Preset tickOrCross(final Boolean permitted, final String what) {
        if (permitted == null) {
            return null;
        }
        return permitted
                ? new Preset(SvgImage.TICK, "Permitted to " + what, true)
                : new Preset(SvgImage.CLOSE, "Not permitted to " + what, true);
    }

    private String grantedBy(final Row row) {
        final AiToolEffectiveGrant grant = effectiveGrants == null
                ? null
                : effectiveGrants.get(row.category());
        return String.join(", ", AiToolPolicyText.grantedBy(grant, row.tool()));
    }

    private SafeHtml descriptionCell(final Row row) {
        if (row.tool() == null) {
            return AiToolPolicyText.oneLine(row.category().getDescription(), null, row.category().getDescription());
        }
        return AiToolPolicyText.oneLine(row.tool().getDescription(), null, row.tool().getDescription());
    }


    // --------------------------------------------------------------------------------


    // A category, or one of its tools.
    private record Row(AiToolCategory category, AiToolSummary tool) {

    }
}
