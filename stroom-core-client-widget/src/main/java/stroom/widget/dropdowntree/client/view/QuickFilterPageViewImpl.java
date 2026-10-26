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

package stroom.widget.dropdowntree.client.view;

import stroom.widget.presenter.client.ViewWithUiHandlers;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.function.Supplier;

/// [QuickFilterPage] as a presenter's view, for the presenters that haven't become controllers
/// yet.
public class QuickFilterPageViewImpl extends ViewWithUiHandlers<QuickFilterUiHandlers>
        implements QuickFilterPageView {

    private final QuickFilterPage page;

    /// @param page The page.
    @Inject
    public QuickFilterPageViewImpl(final QuickFilterPage page) {
        this.page = page;
        page.setOnFilterChange(text -> getUiHandlers().onFilterChange(text));
    }

    @Override
    public void setLabel(final String label) {
        page.setLabel(label);
    }

    @Override
    public void setHelpText(final SafeHtml helpText) {
        page.setHelpText(helpText);
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        page.registerPopupTextProvider(popupTextSupplier);
    }

    @Override
    public void focus() {
        page.focus();
    }

    @Override
    public void setDataView(final IsWidget view) {
        page.setContent(view);
    }

    @Override
    public void setQuickFilterText(final String quickFilterText) {
        page.setQuickFilterText(quickFilterText);
    }

    @Override
    public void setFilterError(final String filterError) {
        page.setFilterError(filterError);
    }

    @Override
    public void setAccessibleName(final String accessibleName) {
        page.setAccessibleName(accessibleName);
    }

    @Override
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        page.setQuickFilterContextHandler(contextHandler);
    }

    @Override
    public Widget asWidget() {
        return page.asWidget();
    }
}
