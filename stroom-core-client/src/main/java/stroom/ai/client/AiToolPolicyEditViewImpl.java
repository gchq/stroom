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

package stroom.ai.client;

import stroom.ai.client.AiToolPolicyEditPresenter.AiToolPolicyEditView;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolCategoryGrant;
import stroom.item.client.SelectionBox;
import stroom.util.shared.NullSafe;
import stroom.widget.help.client.HelpButton;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The tool policy dialog: whether it is enabled, its name and description, and a table of the categories of
/// tools, each with a tick box, a help icon and its access, so the access boxes line up.
public class AiToolPolicyEditViewImpl extends ViewImpl implements AiToolPolicyEditView {

    private static final int TICK_COLUMN = 0;
    private static final int HELP_COLUMN = 1;
    private static final int ACCESS_COLUMN = 2;

    private final Widget widget;
    // One row per category, in the order they are declared.
    private final Map<AiToolCategory, CategoryRow> rows = new LinkedHashMap<>();

    @UiField
    CustomCheckBox enabled;
    @UiField
    TextBox name;
    @UiField
    TextArea description;
    @UiField
    Grid categories;

    /// @param binder Binds the view's fields to its UiBinder template.
    @Inject
    public AiToolPolicyEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        final AiToolCategory[] values = AiToolCategory.values();
        categories.resize(values.length, 3);
        for (int i = 0; i < values.length; i++) {
            final CategoryRow row = new CategoryRow(values[i]);
            rows.put(values[i], row);
            categories.setWidget(i, TICK_COLUMN, row.tick);
            categories.setWidget(i, HELP_COLUMN, row.help);
            categories.setWidget(i, ACCESS_COLUMN, row.access);
        }
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public boolean isEnabled() {
        return enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public String getName() {
        return name.getValue();
    }

    @Override
    public void setName(final String name) {
        this.name.setValue(name);
    }

    @Override
    public String getDescription() {
        return description.getValue();
    }

    @Override
    public void setDescription(final String description) {
        this.description.setValue(description);
    }

    @Override
    public List<AiToolCategoryGrant> getGrants() {
        final List<AiToolCategoryGrant> grants = new ArrayList<>();
        rows.forEach((category, row) -> {
            if (row.tick.getValue()) {
                grants.add(AiToolCategoryGrant.of(category, row.access.getValue()));
            }
        });
        return grants;
    }

    @Override
    public void setGrants(final List<AiToolCategoryGrant> grants) {
        rows.values().forEach(row -> row.show(null));
        NullSafe.list(grants).forEach(grant -> {
            final CategoryRow row = rows.get(grant.getCategory());
            if (row != null) {
                row.show(grant.getAccess());
            }
        });
    }


    // --------------------------------------------------------------------------------


    /// Binds the view's fields to its UiBinder template.
    public interface Binder extends UiBinder<Widget, AiToolPolicyEditViewImpl> {

    }


    // --------------------------------------------------------------------------------


    // A category's row: a tick box with its name, a help icon with what the category holds, and its access, which
    // can only be chosen while the category is ticked.
    private static final class CategoryRow {

        private final CustomCheckBox tick = new CustomCheckBox();
        private final HelpButton help;
        private final SelectionBox<AiToolAccess> access = new SelectionBox<>();

        private CategoryRow(final AiToolCategory category) {
            tick.setLabel(category.getDisplayValue());
            tick.setIdentity("aiToolPolicyCategory" + category.name());
            tick.addValueChangeHandler(event -> access.setEnabled(tick.getValue()));
            help = HelpButton.create(category.getDisplayValue(),
                    AiToolPolicyText.paragraphs(category.getDescription()));
            help.setHelpContentHeading(category.getDisplayValue());
            help.preventTabFocus();
            access.addItems(AiToolAccess.values());
            access.setValue(AiToolAccess.READ_WRITE);
            access.setEnabled(false);
        }

        // Ticked with the given access, or unticked if it is null, offering read and write when it is ticked, since
        // the default policy already grants everyone read-only access.
        private void show(final AiToolAccess grantedAccess) {
            tick.setValue(grantedAccess != null);
            access.setValue(Objects.requireNonNullElse(grantedAccess, AiToolAccess.READ_WRITE));
            access.setEnabled(grantedAccess != null);
        }
    }
}
