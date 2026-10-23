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

package stroom.preferences.client;

import stroom.preferences.client.KeyboardShortcutsPresenter.KeyboardShortcutsView;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.function.Consumer;

/// The keyboard shortcuts dialog's view: a filter above the list of shortcuts.
public final class KeyboardShortcutsViewImpl extends ViewImpl implements KeyboardShortcutsView {

    private final Widget widget;
    private Consumer<String> filterHandler;

    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel list;

    @Inject
    public KeyboardShortcutsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        quickFilter.setAccessibleName("Filter keyboard shortcuts");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        quickFilter.focus();
    }

    @Override
    public void setList(final Widget widget) {
        list.setWidget(widget);
    }

    @Override
    public void setFilterHandler(final Consumer<String> handler) {
        this.filterHandler = handler;
    }

    @UiHandler("quickFilter")
    void onQuickFilterValueChange(final ValueChangeEvent<String> e) {
        if (filterHandler != null) {
            filterHandler.accept(e.getValue());
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, KeyboardShortcutsViewImpl> {

    }
}
