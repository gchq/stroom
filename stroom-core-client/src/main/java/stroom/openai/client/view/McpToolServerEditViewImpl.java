/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.openai.client.view;

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionBox;
import stroom.openai.client.presenter.McpToolServerEditPresenter.McpToolServerEditView;
import stroom.openai.client.presenter.McpToolServerEditUiHandlers;
import stroom.openai.shared.McpAuthMode;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.tab.client.view.LinkTabBar;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpToolServerEditViewImpl
        extends ViewWithUiHandlers<McpToolServerEditUiHandlers>
        implements McpToolServerEditView {

    // The policies offered for a tool, in the order they are listed.
    private static final McpToolPolicy[] POLICIES = {
            McpToolPolicy.ALLOW,
            McpToolPolicy.ASK_ALWAYS,
            McpToolPolicy.DENY};

    private final Widget widget;
    // Each tab and the panel it shows, in order.
    private final Map<TabData, FlowPanel> tabs = new LinkedHashMap<>();
    private final TabData toolsTabData;
    private final FlowPanel policyRows = new FlowPanel();
    private final InlineSvgButton addPolicy = new InlineSvgButton();
    private boolean readOnly;

    @UiField
    LinkTabBar tabBar;
    @UiField
    FlowPanel generalTab;
    @UiField
    FlowPanel toolsTab;
    @UiField
    CustomCheckBox enabled;
    @UiField
    TextBox name;
    @UiField
    TextBox endpoint;
    @UiField
    SelectionBox<McpAuthMode> authMode;
    @UiField
    Label authModeWarning;
    @UiField
    SelectionBox<Credential> credential;
    @UiField
    FormGroup apiKeyHeaderNameGroup;
    @UiField
    TextBox apiKeyHeaderName;
    @UiField
    FormGroup apiKeyValuePrefixGroup;
    @UiField
    TextBox apiKeyValuePrefix;
    @UiField
    FormGroup audienceGroup;
    @UiField
    TextBox audience;
    @UiField
    CustomCheckBox trusted;
    @UiField
    FlowPanel toolPolicies;
    @UiField
    Button setHttpClientConfig;
    @UiField
    Button test;

    @Inject
    public McpToolServerEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        test.setIcon(SvgImage.OK);

        addTab("General", generalTab);
        toolsTabData = addTab("Tools", toolsTab);
        tabBar.addSelectionHandler(event -> selectTab(event.getSelectedItem()));
        selectTab(tabs.keySet().iterator().next());

        addPolicy.setSvg(SvgImage.ADD);
        addPolicy.setTitle("Add Tool Policy");
        addPolicy.addStyleName("allow-focus");
        addPolicy.addClickHandler(event -> addPolicyRow("", McpToolPolicy.ASK_ALWAYS).focus());
        toolPolicies.add(policyRows);
        toolPolicies.add(addPolicy);
    }

    private TabData addTab(final String text, final FlowPanel panel) {
        final TabData tab = new TabDataImpl(text, false);
        tabs.put(tab, panel);
        tabBar.addTab(tab);
        return tab;
    }

    private void selectTab(final TabData selected) {
        if (selected != null && tabs.containsKey(selected)) {
            tabBar.selectTab(selected);
            tabs.forEach((tab, panel) -> panel.setVisible(tab == selected));
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
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enabled.setEnabled(!readOnly);
        name.setEnabled(!readOnly);
        endpoint.setEnabled(!readOnly);
        authMode.setEnabled(!readOnly);
        credential.setEnabled(!readOnly);
        apiKeyHeaderName.setEnabled(!readOnly);
        apiKeyValuePrefix.setEnabled(!readOnly);
        audience.setEnabled(!readOnly);
        trusted.setEnabled(!readOnly);
        setHttpClientConfig.setEnabled(!readOnly);
        addPolicy.setEnabled(!readOnly);
        for (int i = 0; i < policyRows.getWidgetCount(); i++) {
            ((PolicyRow) policyRows.getWidget(i)).setEnabled(!readOnly);
        }
    }

    @Override
    public void showToolsTab() {
        selectTab(toolsTabData);
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
    public String getEndpoint() {
        return endpoint.getValue();
    }

    @Override
    public void setEndpoint(final String endpoint) {
        this.endpoint.setValue(endpoint);
    }

    @Override
    public SelectionBox<McpAuthMode> getAuthModeSelectionBox() {
        return authMode;
    }

    @Override
    public SelectionBox<Credential> getCredentialSelectionBox() {
        return credential;
    }

    @Override
    public void setCredentialEnabled(final boolean enabled) {
        credential.setEnabled(enabled);
    }

    @Override
    public String getApiKeyHeaderName() {
        return apiKeyHeaderName.getValue();
    }

    @Override
    public void setApiKeyHeaderName(final String apiKeyHeaderName) {
        this.apiKeyHeaderName.setValue(apiKeyHeaderName);
    }

    @Override
    public String getApiKeyValuePrefix() {
        return apiKeyValuePrefix.getValue();
    }

    @Override
    public void setApiKeyValuePrefix(final String apiKeyValuePrefix) {
        this.apiKeyValuePrefix.setValue(apiKeyValuePrefix);
    }

    @Override
    public boolean isTrusted() {
        return trusted.getValue();
    }

    @Override
    public void setTrusted(final boolean trusted) {
        this.trusted.setValue(trusted);
    }

    @Override
    public List<McpToolPolicyRule> getToolPolicies() {
        final List<McpToolPolicyRule> rules = new ArrayList<>();
        for (int i = 0; i < policyRows.getWidgetCount(); i++) {
            final PolicyRow row = (PolicyRow) policyRows.getWidget(i);
            final String pattern = row.pattern.getText().trim();
            if (!pattern.isEmpty()) {
                rules.add(new McpToolPolicyRule(pattern, row.policy.getValue()));
            }
        }
        return rules;
    }

    @Override
    public void setToolPolicies(final List<McpToolPolicyRule> rules) {
        policyRows.clear();
        if (rules != null) {
            for (final McpToolPolicyRule rule : rules) {
                addPolicyRow(rule.getToolPattern(), rule.getPolicy());
            }
        }
    }

    private PolicyRow addPolicyRow(final String pattern, final McpToolPolicy policy) {
        final PolicyRow row = new PolicyRow(pattern, policy);
        row.setEnabled(!readOnly);
        policyRows.add(row);
        return row;
    }

    @Override
    public void setAuthModeWarning(final String warning) {
        authModeWarning.setText(warning);
        authModeWarning.setVisible(warning != null);
    }

    @Override
    public String getAudience() {
        return audience.getValue();
    }

    @Override
    public void setAudience(final String audience) {
        this.audience.setValue(audience);
    }

    @Override
    public void setAudienceVisible(final boolean visible) {
        audienceGroup.setVisible(visible);
    }

    @Override
    public void setApiKeyFieldsVisible(final boolean visible) {
        apiKeyHeaderNameGroup.setVisible(visible);
        apiKeyValuePrefixGroup.setVisible(visible);
    }

    @UiHandler("authMode")
    public void onAuthMode(final ValueChangeEvent<McpAuthMode> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onAuthModeChange();
        }
    }

    @UiHandler("setHttpClientConfig")
    public void onSetHttpClientConfig(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetHttpClientConfiguration();
        }
    }

    @UiHandler("test")
    public void onTest(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTest();
        }
    }

    public interface Binder extends UiBinder<Widget, McpToolServerEditViewImpl> {

    }

    // A tool policy: a pattern for tool names, what to do with them, and a button to remove it.
    private static final class PolicyRow extends FlowPanel {

        private final TextBox pattern = new TextBox();
        private final SelectionBox<McpToolPolicy> policy = new SelectionBox<>();
        private final InlineSvgButton remove = new InlineSvgButton();

        private PolicyRow(final String toolPattern, final McpToolPolicy toolPolicy) {
            setStyleName("mcpToolServer-policy");
            pattern.setText(toolPattern);
            pattern.getElement().setAttribute("placeholder", "Tool name pattern, e.g. find_.*");
            pattern.setTitle("A regular expression that must match the tool's whole name, ignoring case");
            pattern.addStyleName("mcpToolServer-policyPattern allow-focus");
            policy.addItems(POLICIES);
            policy.setValue(toolPolicy != null
                    ? toolPolicy
                    : McpToolPolicy.ASK_ALWAYS);
            policy.addStyleName("mcpToolServer-policyValue");
            remove.setSvg(SvgImage.DELETE);
            remove.setTitle("Remove Tool Policy");
            remove.addStyleName("allow-focus");
            remove.addClickHandler(event -> removeFromParent());
            add(pattern);
            add(policy);
            add(remove);
        }

        private void setEnabled(final boolean enabled) {
            pattern.setEnabled(enabled);
            policy.setEnabled(enabled);
            remove.setEnabled(enabled);
        }

        private void focus() {
            pattern.setFocus(true);
        }
    }
}
