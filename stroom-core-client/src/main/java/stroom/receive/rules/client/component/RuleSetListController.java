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
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// A rule set's data receipt rules ([RuleSetList]), as its owner gives them.
public class RuleSetListController extends Controller implements Focus {

    private RuleSetList view;

    /// Made by GIN; [#attach(RuleSetList)] gives it its list.
    ///
    /// @param eventBus The application's event bus.
    @Inject
    public RuleSetListController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public RuleSetListController attach(final RuleSetList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        return this;
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
}
