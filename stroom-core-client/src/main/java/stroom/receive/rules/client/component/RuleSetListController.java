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

package stroom.receive.rules.client.component;

import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// A rule set's data receipt rules ([RuleSetList]), as its owner gives them.
public class RuleSetListController extends Controller implements Focus {

    private final RuleSetList view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public RuleSetListController(final EventBus eventBus,
                                 final RuleSetList view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// @param data The rules.
    public void setData(final List<ReceiveDataRule> data) {
        view.setData(data);
    }

    /// @return The selection.
    public MultiSelectionModel<ReceiveDataRule> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }
}
