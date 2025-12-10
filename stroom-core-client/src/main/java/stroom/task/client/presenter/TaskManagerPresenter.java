/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.task.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.task.client.presenter.TaskManagerPresenter.TaskManagerView;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.function.Supplier;

public class TaskManagerPresenter
        extends ContentTabPresenter<TaskManagerView>
        implements Refreshable, TaskManagerUiHandlers {

    public static final String TAB_TYPE = "ServerTasks";
    private final TaskManagerListPresenter listPresenter;

    @Inject
    public TaskManagerPresenter(final EventBus eventBus,
                                final TaskManagerView view,
                                final TaskManagerListPresenter listPresenter,
                                final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setUiHandlers(this);
        view.setList(listPresenter.getWidget());

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
        listPresenter.refresh();
    }

    @Override
    public void changeNameFilter(final String name) {
        getView().setNameFilter(name);
        listPresenter.setNameFilter(name);
    }

    public void changeNameFilter(final String nodeName, final String taskName, final String userName) {
        final StringBuilder sb = new StringBuilder();
        boolean hasAdded;
        hasAdded = addField(sb, FindTaskProgressCriteria.FIELD_NODE, nodeName);
        hasAdded = addField(sb, FindTaskProgressCriteria.FIELD_NAME, taskName) || hasAdded;
        hasAdded = addField(sb, FindTaskProgressCriteria.FIELD_USER, userName) || hasAdded;
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
            getView().focusFilter();
            return true;
        }
        return false;
    }

    // --------------------------------------------------------------------------------


    public interface TaskManagerView extends View, HasUiHandlers<TaskManagerUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void setList(Widget widget);

        void focusFilter();

        void setNameFilter(final String nameFilter);
    }
}
