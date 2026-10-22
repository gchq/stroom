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

package stroom.openai.client.presenter;

import stroom.ai.shared.AiToolCategory;
import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.http.client.presenter.HttpClientConfigPresenter;
import stroom.item.client.SelectionBox;
import stroom.openai.client.presenter.McpToolServerEditPresenter.McpToolServerEditView;
import stroom.openai.shared.McpAuthMode;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelResource;
import stroom.openai.shared.TestMcpToolServerRequest;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.regexp.shared.RegExp;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class McpToolServerEditPresenter
        extends MyPresenterWidget<McpToolServerEditView>
        implements McpToolServerEditUiHandlers {

    // As on the server (McpToolFilter): tool names are short, so a longer pattern is a mistake.
    static final int MAX_PATTERN_LENGTH = 500;
    // The text of the empty tool category option: a server with no tool category is in Core.
    private static final String DEFAULT_TOOL_CATEGORY_TEXT = "Default (Core)";

    private final EventBus eventBus;
    private final CredentialClient credentialClient;
    private final Provider<HttpClientConfigPresenter> httpClientConfigPresenterProvider;
    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;

    private HttpClientConfig httpClientConfiguration;
    private boolean readOnly;
    private String modelUuid;

    @Inject
    public McpToolServerEditPresenter(
            final EventBus eventBus,
            final McpToolServerEditView view,
            final CredentialClient credentialClient,
            final Provider<HttpClientConfigPresenter> httpClientConfigPresenterProvider,
            final UiConfigCache uiConfigCache,
            final RestFactory restFactory) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;
        this.eventBus = eventBus;
        this.credentialClient = credentialClient;
        this.httpClientConfigPresenterProvider = httpClientConfigPresenterProvider;
        view.setUiHandlers(this);
        // In alphabetical order of what they are called.
        view.getAuthModeSelectionBox().addItems(Arrays.stream(McpAuthMode.values())
                .sorted(Comparator.comparing(McpAuthMode::getDisplayValue, String.CASE_INSENSITIVE_ORDER))
                .toList());
        // No category means Core, so the empty option says so.
        view.getToolCategorySelectionBox().setNonSelectString(DEFAULT_TOOL_CATEGORY_TEXT);
        view.getToolCategorySelectionBox().addItems(AiToolCategory.values());
    }

    /// Shows the dialog.
    ///
    /// @param config    The server's settings.
    /// @param readOnly  True if the settings can only be looked at.
    /// @param modelUuid The AI model the server belongs to, for testing the connection.
    /// @param consumer  Given the settings when the dialog closes: the edited ones on OK, otherwise the original.
    public void show(final McpToolServerConfig config,
                     final boolean readOnly,
                     final String modelUuid,
                     final Consumer<McpToolServerConfig> consumer) {
        this.readOnly = readOnly;
        this.modelUuid = modelUuid;
        read(config);
        getView().onReadOnly(readOnly);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(640, 560))
                .caption("Edit MCP Tool Server")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final McpToolServerConfig written = write();
                        final String policiesError = policiesError(written.getToolPolicies());
                        if (policiesError != null) {
                            getView().showToolsTab();
                            AlertEvent.fireError(McpToolServerEditPresenter.this, policiesError, null);
                            e.reset();
                            return;
                        }
                        final String error = validate(written);
                        if (error != null) {
                            AlertEvent.fireError(McpToolServerEditPresenter.this, error, null);
                            e.reset();
                        } else {
                            consumer.accept(written);
                            e.hide();
                        }
                    } else {
                        consumer.accept(config);
                        e.hide();
                    }
                })
                .fire();
    }

    private String validate(final McpToolServerConfig config) {
        if (NullSafe.isBlankString(config.getName())) {
            return "A name is required.";
        }
        if (NullSafe.isBlankString(config.getEndpoint())) {
            return "An endpoint URL is required.";
        }
        final String endpoint = config.getEndpoint().toLowerCase();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            return "The endpoint URL must start with http:// or https://";
        }
        if (usesCredential(config.getAuthMode()) && NullSafe.isBlankString(config.getCredentialName())) {
            return "A credential is required for authentication mode '"
                   + config.getAuthMode().getDisplayValue() + "'.";
        }
        return null;
    }

    private static boolean usesCredential(final McpAuthMode authMode) {
        return authMode == McpAuthMode.BEARER_TOKEN
               || authMode == McpAuthMode.API_KEY
               || authMode == McpAuthMode.BASIC;
    }

    @Override
    public void onAuthModeChange() {
        updateCredentialModel(getView().getAuthModeSelectionBox().getValue());
        updateAudienceVisibility(getView().getAuthModeSelectionBox().getValue());
        getView().setApiKeyFieldsVisible(getView().getAuthModeSelectionBox().getValue() == McpAuthMode.API_KEY);
    }

    @Override
    public void onTest() {
        final McpToolServerConfig config = write();
        final String policiesError = policiesError(config.getToolPolicies());
        final String error = policiesError != null
                ? policiesError
                : validate(config);
        if (error != null) {
            AlertEvent.fireError(this, error, null);
            return;
        }
        // Created here rather than statically, so the validation in this class can be unit tested.
        final OpenAIModelResource resource = GWT.create(OpenAIModelResource.class);
        restFactory
                .create(resource)
                .method(res -> res.testMcpToolServer(new TestMcpToolServerRequest(modelUuid, config)))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Successful", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failed", result.getMessage(), null);
                    }
                })
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    public void onSetHttpClientConfiguration() {
        if (httpClientConfiguration != null) {
            showHttpClientConfiguration();
            return;
        }
        // Start from the defaults, as an empty config would be saved with zero timeouts.
        final OpenAIModelResource resource = GWT.create(OpenAIModelResource.class);
        restFactory
                .create(resource)
                .method(OpenAIModelResource::getDefaultHttpClientConfig)
                .onSuccess(defaults -> {
                    httpClientConfiguration = defaults;
                    showHttpClientConfiguration();
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void showHttpClientConfiguration() {
        httpClientConfigPresenterProvider.get().show(httpClientConfiguration, readOnly, updated -> {
            if (!Objects.equals(httpClientConfiguration, updated)) {
                httpClientConfiguration = updated;
            }
        });
    }

    /// Shows the token audience for 'OAuth2 passthrough' when Stroom is its own identity provider, as Stroom then
    /// issues the tokens itself.
    private void updateAudienceVisibility(final McpAuthMode authMode) {
        getView().setAudienceVisible(false);
        if (authMode == McpAuthMode.STROOM_SESSION_TOKEN) {
            uiConfigCache.get(config -> {
                if (config != null && !config.isExternalIdentityProvider()) {
                    // Stroom issues its own tokens for the server, which need an audience.
                    getView().setAudienceVisible(true);
                }
            }, this);
        }
    }

    private void updateCredentialModel(final McpAuthMode authMode) {
        final Set<CredentialType> types = authMode == McpAuthMode.BASIC
                ? Set.of(CredentialType.USERNAME_PASSWORD)
                : Set.of(CredentialType.ACCESS_TOKEN);
        final CredentialListModel model = new CredentialListModel(eventBus, credentialClient, types);
        model.setTaskMonitorFactory(this);
        getView().getCredentialSelectionBox().setModel(model);
        getView().setCredentialEnabled(!readOnly && usesCredential(authMode));
        // None and OAuth2 passthrough use no stored credential.
        getView().setCredentialVisible(usesCredential(authMode));
    }

    private void read(final McpToolServerConfig config) {
        final McpToolServerConfig c = config != null
                ? config
                : McpToolServerConfig.builder().build();
        getView().setEnabled(c.isEnabled());
        getView().setName(c.getName());
        getView().setEndpoint(c.getEndpoint());
        getView().getAuthModeSelectionBox().setValue(c.getAuthMode());
        updateCredentialModel(c.getAuthMode());
        updateAudienceVisibility(c.getAuthMode());
        if (NullSafe.isNonBlankString(c.getCredentialName())) {
            credentialClient.getCredentialByName(c.getCredentialName(),
                    credential -> getView().getCredentialSelectionBox().setValue(credential), this);
        } else {
            getView().getCredentialSelectionBox().setValue(null);
        }
        getView().setApiKeyHeaderName(NullSafe.nonBlankStringElse(
                c.getApiKeyHeaderName(), McpToolServerConfig.DEFAULT_API_KEY_HEADER_NAME));
        getView().setApiKeyValuePrefix(c.getApiKeyValuePrefix());
        getView().setAudience(c.getAudience());
        getView().setApiKeyFieldsVisible(c.getAuthMode() == McpAuthMode.API_KEY);
        getView().setTrusted(c.isTrusted());
        getView().setToolPolicies(c.getToolPolicies());
        getView().getToolCategorySelectionBox().setValue(c.getToolCategory());
        this.httpClientConfiguration = c.getHttpClientConfiguration();
    }

    private McpToolServerConfig write() {
        return McpToolServerConfig.builder()
                .enabled(getView().isEnabled())
                .name(NullSafe.trim(getView().getName()))
                .endpoint(NullSafe.trim(getView().getEndpoint()))
                .authMode(getView().getAuthModeSelectionBox().getValue())
                .credentialName(NullSafe.get(
                        getView().getCredentialSelectionBox().getValue(), Credential::getName))
                .apiKeyHeaderName(NullSafe.trim(getView().getApiKeyHeaderName()))
                .apiKeyValuePrefix(getView().getApiKeyValuePrefix())
                .audience(NullSafe.trim(getView().getAudience()))
                .httpClientConfiguration(httpClientConfiguration)
                .trusted(getView().isTrusted())
                .toolPolicies(NullSafe.isEmptyCollection(getView().getToolPolicies())
                        ? null
                        : getView().getToolPolicies())
                .toolCategory(getView().getToolCategorySelectionBox().getValue())
                .build();
    }

    /// @param rules Tool policies.
    /// @return Why the first policy that cannot be used cannot be, or null if they can all be used.
    static String policiesError(final List<McpToolPolicyRule> rules) {
        for (final McpToolPolicyRule rule : NullSafe.list(rules)) {
            final String pattern = rule.getToolPattern();
            if (pattern.length() > MAX_PATTERN_LENGTH) {
                return "The tool policy pattern '" + pattern.substring(0, 40) + "…' is longer than "
                       + MAX_PATTERN_LENGTH + " characters.";
            }
            try {
                RegExp.compile(pattern);
            } catch (final RuntimeException e) {
                return "The tool policy pattern '" + pattern + "' is not a valid regular expression: "
                       + reason(e);
            }
        }
        return null;
    }

    // The browser's message repeats the pattern ("Invalid regular expression: /a(/: Unterminated group"), so
    // keep only the reason at the end.
    private static String reason(final RuntimeException e) {
        final String message = NullSafe.nonBlankStringElse(e.getMessage(), e.toString());
        final int idx = message.lastIndexOf(": ");
        return idx >= 0 && idx + 2 < message.length()
                ? message.substring(idx + 2)
                : message;
    }

    public interface McpToolServerEditView
            extends View, HasUiHandlers<McpToolServerEditUiHandlers> {

        void focus();

        void onReadOnly(boolean readOnly);

        boolean isEnabled();

        void setEnabled(boolean enabled);

        String getName();

        void setName(String name);

        String getEndpoint();

        void setEndpoint(String endpoint);

        SelectionBox<McpAuthMode> getAuthModeSelectionBox();

        SelectionBox<Credential> getCredentialSelectionBox();

        void setCredentialEnabled(boolean enabled);

        String getApiKeyHeaderName();

        void setApiKeyHeaderName(String apiKeyHeaderName);

        String getApiKeyValuePrefix();

        void setApiKeyValuePrefix(String apiKeyValuePrefix);

        /// Shows the credential, used only by the modes that send a stored credential.
        void setCredentialVisible(boolean visible);

        String getAudience();

        void setAudience(String audience);

        /// Shows the token audience, used for 'OAuth2 passthrough' with Stroom's internal identity provider.
        void setAudienceVisible(boolean visible);

        void setApiKeyFieldsVisible(boolean visible);

        boolean isTrusted();

        void setTrusted(boolean trusted);

        /// @return The tool policies, in order, leaving out any with no pattern.
        List<McpToolPolicyRule> getToolPolicies();

        /// @param rules The tool policies, in order, or null for none.
        void setToolPolicies(List<McpToolPolicyRule> rules);

        /// @return The box for the tool policy category that grants the server's tools, where a tool names none
        /// itself. An empty value means Core.
        SelectionBox<AiToolCategory> getToolCategorySelectionBox();

        /// Shows the Tool Policies tab, e.g. to point out a problem with a tool policy.
        void showToolsTab();
    }
}
