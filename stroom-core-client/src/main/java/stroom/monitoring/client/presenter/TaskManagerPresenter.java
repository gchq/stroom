/*
 * Copyright 2016 Crown Copyright
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

package stroom.monitoring.client.presenter;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.monitoring.client.presenter.TaskManagerPresenter.TaskManagerView;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;

public class TaskManagerPresenter extends ContentTabPresenter<TaskManagerView>
        implements Refreshable, TaskManagerUiHandlers {
    private final TaskManagerListPresenter listPresenter;

    @Inject
    public TaskManagerPresenter(final EventBus eventBus,
                                final TaskManagerView view,
                                final TaskManagerListPresenter listPresenter) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setUiHandlers(this);
        view.setList(listPresenter.getWidget());
    }

    @Override
    public String getLabel() {
        return "Server Tasks";
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.JOBS;
    }

    @Override
    public void refresh() {
        listPresenter.refresh();
    }

    @Override
    public void changeNameFilter(final String name) {
        listPresenter.setNameFilter(name);
    }

    public interface TaskManagerView extends View, HasUiHandlers<TaskManagerUiHandlers> {
        void setList(Widget widget);
    }
}
