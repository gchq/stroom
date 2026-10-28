/*
 * Copyright 2020 Crown Copyright
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

package stroom.importexport.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.importexport.shared.DependencyCriteria;
import stroom.query.api.datasource.ConditionSet;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the documents' dependencies, filtered as the user asks.
public class DependenciesTabController extends ContentTabController {

    public static final String TAB_TYPE = "Dependencies";

    private final DependenciesTab view;

    private final DependencyListController dependencyList;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public DependenciesTabController(final EventBus eventBus,
                                     final DependenciesTab view,
                                     final DependencyListController dependencyList,
                                     final UiConfigCache uiConfigCache,
                                     final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(dependencyList);
        this.dependencyList = dependencyList;
        view.setOnFilterChange(this::changeQuickFilter);
        view.setListView(dependencyList);
        // A rejected filter comes back as an empty grid like any other, so surface the reason on
        // the filter box. See ResultPage.filterError.
        dependencyList.setFilterErrorConsumer(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                DependencyCriteria.QUICK_FILTER_CONTEXT, this, this));

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.setHelpTooltipText(QuickFilterTooltipUtil.createTooltip(
                        "Dependencies Quick Filter Syntax",
                        null,
                        DependencyCriteria.FIELD_DEFINITIONS,
                        // The only DB-backed surface with a quick filter tooltip. SQL_TEXT has no
                        // WORD_BOUNDARY, so without this the tooltip would advertise "?ABC" on a
                        // screen that now rejects it.
                        ConditionSet.SQL_TEXT,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        view.focusFilter();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DEPENDENCIES;
    }

    @Override
    public String getLabel() {
        return "Dependencies";
    }

    /// Shows what matches a filter.
    ///
    /// @param name The filter.
    public void changeQuickFilter(final String name) {
        if (NullSafe.isNonEmptyString(name)) {
            dependencyList.setFilterInput(name);
        } else {
            dependencyList.clearFilterInput();
        }
        dependencyList.refresh();
    }

    /// Filters the dependencies, as if the user typed the filter.
    ///
    /// @param text The filter.
    public void setQuickFilterText(final String text) {
        view.setQuickFilterText(text);
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
