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

package stroom.importexport.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.importexport.client.presenter.DependenciesTabPresenter.DependenciesTabView;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class DependenciesTabPresenter
        extends ContentTabPresenter<DependenciesTabView>
        implements DependenciesUiHandlers {

    public static final String TAB_TYPE = "Dependencies";
    public static final String LIST = "LIST";

    private final DependenciesPresenter dependenciesPresenter;

    @Inject
    public DependenciesTabPresenter(final EventBus eventBus,
                                    final DependenciesTabView view,
                                    final DependenciesPresenter dependenciesPresenter,
                                    final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.dependenciesPresenter = dependenciesPresenter;
        view.setUiHandlers(this);
        setInSlot(LIST, dependenciesPresenter);

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.setHelpTooltipText(QuickFilterTooltipUtil.createTooltip(
                        "Dependencies Quick Filter Syntax",
                        DependencyCriteria.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    @Override
    protected void onBind() {
        getView().focusFilter();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DEPENDENCIES;
    }

    @Override
    public String getLabel() {
        return "Dependencies";
    }

    @Override
    public void changeQuickFilter(final String name) {
        if (NullSafe.isNonEmptyString(name)) {
            dependenciesPresenter.setFilterInput(name);
        } else {
            dependenciesPresenter.clearFilterInput();
        }
        dependenciesPresenter.refresh();
    }

    public void setQuickFilterText(final String text) {
        getView().setQuickFilterText(text);
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

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        dependenciesPresenter.setTaskMonitorFactory(taskMonitorFactory);
    }

    // --------------------------------------------------------------------------------


    public interface DependenciesTabView extends View, HasUiHandlers<DependenciesUiHandlers> {

        void setHelpTooltipText(final SafeHtml helpTooltipText);

        void setQuickFilterText(final String text);

        void focusFilter();
    }
}
