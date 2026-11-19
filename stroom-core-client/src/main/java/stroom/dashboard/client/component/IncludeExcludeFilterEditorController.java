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

package stroom.dashboard.client.component;

import stroom.query.api.IncludeExcludeFilter;
import stroom.widget.component.client.Controller;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// Shows and changes a column's include and exclude filter ([IncludeExcludeFilterEditor]).
public class IncludeExcludeFilterEditorController extends Controller implements Focus {

    private final IncludeExcludeFilterEditor view;
    private final IncludeExcludeFilterDictionaryController includeDictionaries;
    private final IncludeExcludeFilterDictionaryController excludeDictionaries;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param dictionariesProvider Makes the controllers of its two lists of dictionaries.
    @Inject
    public IncludeExcludeFilterEditorController(
            final EventBus eventBus,
            final IncludeExcludeFilterEditor view,
            final Provider<IncludeExcludeFilterDictionaryController> dictionariesProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        includeDictionaries = make(dictionariesProvider);
        includeDictionaries.attach(view.getIncludeDictionaries());
        excludeDictionaries = make(dictionariesProvider);
        excludeDictionaries.attach(view.getExcludeDictionaries());
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// Shows a filter.
    ///
    /// @param filter The filter, or `null` for none.
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

        includeDictionaries.setDictionaries(filter == null
                ? null
                : filter.getIncludeDictionaries());
        excludeDictionaries.setDictionaries(filter == null
                ? null
                : filter.getExcludeDictionaries());

        view.setIncludes(includes);
        view.setExcludes(excludes);
    }

    /// @return The filter shown, or `null` if it filters nothing.
    public IncludeExcludeFilter getFilter() {
        String includes = null;
        String excludes = null;
        if (view.getIncludes() != null && !view.getIncludes().trim().isEmpty()) {
            includes = view.getIncludes().trim();
        }
        if (view.getExcludes() != null && !view.getExcludes().trim().isEmpty()) {
            excludes = view.getExcludes().trim();
        }

        IncludeExcludeFilter filter = null;
        if (includes != null || excludes != null
            || !includeDictionaries.getDictionaries().isEmpty()
            || !excludeDictionaries.getDictionaries().isEmpty()) {
            filter = new IncludeExcludeFilter(includes, excludes,
                    includeDictionaries.getDictionaries(),
                    excludeDictionaries.getDictionaries());
        }
        return filter;
    }
}
