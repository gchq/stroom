/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.IncludeExcludeFilterPresenter.IncludeExcludeFilterView;
import stroom.query.api.IncludeExcludeFilter;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class IncludeExcludeFilterPresenter extends MyPresenterWidget<IncludeExcludeFilterView> {

    @Inject
    public IncludeExcludeFilterPresenter(final EventBus eventBus, final IncludeExcludeFilterView view) {
        super(eventBus, view);
    }

    public void setFilter(final IncludeExcludeFilter filter) {
        String includes = "";
        String excludes = "";

        if (filter != null) {
            if (filter.getIncludes() != null) {
                includes = filter.getIncludes();
            }
            if (filter.getExcludes() != null) {
                excludes = filter.getExcludes();
            }
        }

        getView().setIncludes(includes);
        getView().setExcludes(excludes);
    }

    public IncludeExcludeFilter getFilter() {
        String includes = null;
        String excludes = null;
        if (getView().getIncludes() != null && getView().getIncludes().trim().length() > 0) {
            includes = getView().getIncludes().trim();
        }
        if (getView().getExcludes() != null && getView().getExcludes().trim().length() > 0) {
            excludes = getView().getExcludes().trim();
        }

        IncludeExcludeFilter filter = null;
        if (includes != null || excludes != null) {
            filter = new IncludeExcludeFilter(includes, excludes);
        }
        return filter;
    }

    public interface IncludeExcludeFilterView extends View, Focus {

        String getIncludes();

        void setIncludes(String includes);

        String getExcludes();

        void setExcludes(String excludes);
    }
}
