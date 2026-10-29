/*
 * Copyright 2019 Crown Copyright
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

package stroom.task.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.data.table.client.Refreshable;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.TableCell;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;


/// Shows the tasks running on every node, filtered as the user asks.
public class ServerTasksController extends ContentTabController
        implements Refreshable {

    public static final String TAB_TYPE = "ServerTasks";

    private final ServerTasks view;

    private final TaskManagerListController taskManagerList;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             Its component.
    /// @param taskManagerList                  The task manager list.
    /// @param uiConfigCache                    The UI's configuration.
    /// @param quickFilterContextHandlerFactory Adds the quick filter's history and Advanced Query.
    @Inject
    public ServerTasksController(final EventBus eventBus,
                                 final ServerTasks view,
                                 final TaskManagerListController taskManagerList,
                                 final UiConfigCache uiConfigCache,
                                 final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.taskManagerList = own(taskManagerList).attach(view.getList());
        view.setOnFilterChange(this::changeNameFilter);
        // A rejected filter comes back as an empty list like any other, so surface the reason on
        // the filter box. See ResultPage.filterError.
        taskManagerList.setFilterErrorConsumer(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                FindTaskProgressCriteria.QUICK_FILTER_CONTEXT, this, this));

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Server Tasks Quick Filter",
                        builder -> builder
                                .row(
                                        TableCell.data(
                                                "Matched tasks are displayed in black, un-matched but " +
                                                        "related tasks are displayed in grey.", 2))
                                .row(
                                        TableCell.data("All relations of a matched task will be " +
                                                "included in the results.", 2))
                                .row(),
                        FindTaskProgressCriteria.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    @Override
    public String getLabel() {
        return "Server Tasks";
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.JOBS;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public void refresh() {
        taskManagerList.refresh();
    }

    /// Shows the tasks whose name, node or user match a filter.
    ///
    /// @param name The filter.
    public void changeNameFilter(final String name) {
        view.setNameFilter(name);
        taskManagerList.setNameFilter(name);
    }

    /// Filters the tasks; each filter left blank is left out.
    ///
    /// @param nodeName The node's name.
    /// @param taskName The task's name.
    public void changeNameFilter(final String nodeName, final String taskName) {
        final StringBuilder sb = new StringBuilder();
        boolean hasAdded;
        hasAdded = addField(sb, FindTaskProgressCriteria.FIELD_NODE, nodeName);
        hasAdded = addField(sb, FindTaskProgressCriteria.FIELD_NAME, taskName) || hasAdded;
        if (hasAdded) {
            changeNameFilter(sb.toString().trim());
        }
    }

    private boolean addField(final StringBuilder sb, final String fieldName, final String value) {
        boolean wasAdded = false;
        if (NullSafe.isNonBlankString(value)) {
            //noinspection SizeReplaceableByIsEmpty
            if (sb.length() > 0) {
                sb.append(" ");
            }
            if (value.contains(" ")) {
                sb.append("\"");
            }
            sb.append(fieldName.toLowerCase())
                    .append(":")
                    .append(value);
            if (value.contains(" ")) {
                sb.append("\"");
            }
            wasAdded = true;
        }
        return wasAdded;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.FOCUS_FILTER == action) {
            view.focusFilter();
            return true;
        }
        return false;
    }
}
