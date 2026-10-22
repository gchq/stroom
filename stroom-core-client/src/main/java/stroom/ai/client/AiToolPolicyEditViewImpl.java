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
import stroom.item.client.SelectionBox;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The tool policy dialog.
public class AiToolPolicyEditViewImpl
        extends ViewWithUiHandlers<AiToolPolicyEditUiHandlers>
        implements AiToolPolicyEditView {

    private final Widget widget;
    // One tick box per category, in the order they are declared.
    private final Map<AiToolCategory, CustomCheckBox> categoryCheckBoxes = new LinkedHashMap<>();
    private final FlowPanel memberRows = new FlowPanel();
    private final InlineSvgButton addMember = new InlineSvgButton();
    private boolean membersEnabled = true;

    @UiField
    TextBox name;
    @UiField
    TextArea description;
    @UiField
    CustomCheckBox enabled;
    @UiField
    CustomCheckBox allUsers;
    @UiField
    FlowPanel members;
    @UiField
    FlowPanel categories;
    @UiField
    SelectionBox<AiToolAccess> access;
    @UiField
    HTML grantedTools;

    /// @param binder Binds the view's fields to its UiBinder template.
    @Inject
    public AiToolPolicyEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        addMember.setSvg(SvgImage.ADD);
        addMember.setTitle("Add User Or Group");
        addMember.addStyleName("allow-focus");
        addMember.addClickHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onAddMember();
            }
        });
        members.add(memberRows);
        members.add(addMember);

        for (final AiToolCategory category : AiToolCategory.values()) {
            final CustomCheckBox checkBox = new CustomCheckBox();
            checkBox.setLabel(category.getDisplayValue());
            checkBox.setIdentity("aiToolPolicyCategory" + category.name());
            checkBox.addValueChangeHandler(event -> onGrantChange());
            categoryCheckBoxes.put(category, checkBox);
            categories.add(checkBox);
            categories.add(new Label(category.getDescription()));
        }

        access.addItems(AiToolAccess.values());
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
    public boolean isEnabled() {
        return enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public boolean isAllUsers() {
        return allUsers.getValue();
    }

    @Override
    public void setAllUsers(final boolean allUsers) {
        this.allUsers.setValue(allUsers);
    }

    @Override
    public void setMembers(final List<UserRef> members) {
        memberRows.clear();
        for (final UserRef member : NullSafe.list(members)) {
            final MemberRow row = new MemberRow(member);
            row.setEnabled(membersEnabled);
            memberRows.add(row);
        }
        if (memberRows.getWidgetCount() == 0) {
            memberRows.add(new Label("No users or groups."));
        }
    }

    @Override
    public void setMembersEnabled(final boolean enabled) {
        this.membersEnabled = enabled;
        addMember.setEnabled(enabled);
        for (int i = 0; i < memberRows.getWidgetCount(); i++) {
            if (memberRows.getWidget(i) instanceof final MemberRow row) {
                row.setEnabled(enabled);
            }
        }
    }

    @Override
    public List<AiToolCategory> getCategories() {
        final List<AiToolCategory> ticked = new ArrayList<>();
        categoryCheckBoxes.forEach((category, checkBox) -> {
            if (checkBox.getValue()) {
                ticked.add(category);
            }
        });
        return ticked;
    }

    @Override
    public void setCategories(final List<AiToolCategory> categories) {
        final List<AiToolCategory> toTick = NullSafe.list(categories);
        categoryCheckBoxes.forEach((category, checkBox) ->
                checkBox.setValue(toTick.contains(category)));
    }

    @Override
    public AiToolAccess getAccess() {
        return access.getValue();
    }

    @Override
    public void setAccess(final AiToolAccess access) {
        this.access.setValue(access);
    }

    @Override
    public void setGrantedTools(final SafeHtml html) {
        grantedTools.setHTML(html);
    }

    @UiHandler("allUsers")
    void onAllUsers(final ValueChangeEvent<Boolean> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onAllUsersChange();
        }
    }

    @UiHandler("access")
    void onAccess(final ValueChangeEvent<AiToolAccess> event) {
        onGrantChange();
    }

    private void onGrantChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onGrantChange();
        }
    }

    private void removeMember(final UserRef member) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRemoveMember(member);
        }
    }


    // --------------------------------------------------------------------------------


    /// Binds the view's fields to its UiBinder template.
    public interface Binder extends UiBinder<Widget, AiToolPolicyEditViewImpl> {

    }


    // --------------------------------------------------------------------------------


    // A member: its name, and a button to remove it.
    private final class MemberRow extends FlowPanel {

        private final InlineSvgButton remove = new InlineSvgButton();

        private MemberRow(final UserRef member) {
            setStyleName("dock-container-horizontal align-center");
            final Label label = new Label(AiToolPolicyText.memberName(member));
            label.addStyleName("dock-max");
            remove.setSvg(SvgImage.DELETE);
            remove.setTitle("Remove " + AiToolPolicyText.memberName(member));
            remove.addStyleName("dock-min allow-focus");
            remove.addClickHandler(event -> removeMember(member));
            add(label);
            add(remove);
        }

        private void setEnabled(final boolean enabled) {
            remove.setEnabled(enabled);
        }
    }
}
