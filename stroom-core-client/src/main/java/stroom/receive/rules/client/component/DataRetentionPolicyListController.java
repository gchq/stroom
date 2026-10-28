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

import stroom.data.retention.shared.DataRetentionRule;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// The data retention rules ([DataRetentionPolicyList]), as its owner gives them; a rule's action
/// menu has the items its owner gives.
public class DataRetentionPolicyListController extends Controller implements Focus {

    private final DataRetentionPolicyList view;
    private Function<DataRetentionRule, List<Item>> actionMenuItemProvider = rule -> List.of();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public DataRetentionPolicyListController(final EventBus eventBus,
                                             final DataRetentionPolicyList view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnShowActionMenu((row, popupPosition) -> ShowMenuEvent
                .builder()
                .name(DataRetentionPolicyList.getActionMenuName(row))
                .items(actionMenuItemProvider.apply(row))
                .popupPosition(popupPosition)
                .fire(this));
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// @param data The rules.
    public void setData(final List<DataRetentionRule> data) {
        view.setData(data);
    }

    /// @return The selection, one rule at most.
    public MultiSelectionModel<DataRetentionRule> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// @param onEnabledChange Given a rule and whether it is now enabled.
    public void setOnEnabledChange(final BiConsumer<DataRetentionRule, Boolean> onEnabledChange) {
        view.setOnEnabledChange(onEnabledChange);
    }

    /// @param defaultRule The rule that keeps everything else, which can't be disabled.
    public void setDefaultRule(final DataRetentionRule defaultRule) {
        view.setDefaultRuleName(defaultRule.getName());
    }

    /// @param actionMenuItemProvider Gives a rule's action menu.
    public void setActionMenuItemProvider(final Function<DataRetentionRule, List<Item>> actionMenuItemProvider) {
        this.actionMenuItemProvider = actionMenuItemProvider;
    }
}
