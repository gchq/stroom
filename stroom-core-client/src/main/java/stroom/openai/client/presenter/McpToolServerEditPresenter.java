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

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.http.client.presenter.HttpClientConfigPresenter;
import stroom.item.client.SelectionBox;
import stroom.openai.client.presenter.McpToolServerEditPresenter.McpToolServerEditView;
import stroom.openai.shared.McpAuthMode;
import stroom.openai.shared.McpToolGroupRule;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.openai.shared.McpToolServerConfig;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class McpToolServerEditPresenter
        extends MyPresenterWidget<McpToolServerEditView>
        implements McpToolServerEditUiHandlers {

    private final EventBus eventBus;
    private final CredentialClient credentialClient;
    private final Provider<HttpClientConfigPresenter> httpClientConfigPresenterProvider;
    private final UiConfigCache uiConfigCache;

    private HttpClientConfig httpClientConfiguration;
    private boolean readOnly;

    @Inject
    public McpToolServerEditPresenter(
            final EventBus eventBus,
            final McpToolServerEditView view,
            final CredentialClient credentialClient,
            final Provider<HttpClientConfigPresenter> httpClientConfigPresenterProvider,
            final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.eventBus = eventBus;
        this.credentialClient = credentialClient;
        this.httpClientConfigPresenterProvider = httpClientConfigPresenterProvider;
        view.setUiHandlers(this);
        view.getAuthModeSelectionBox().addItems(McpAuthMode.values());
    }

    public void show(final McpToolServerConfig config,
                     final boolean readOnly,
                     final Consumer<McpToolServerConfig> consumer) {
        this.readOnly = readOnly;
        read(config);
        getView().onReadOnly(readOnly);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Edit MCP Tool Server")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final McpToolServerConfig written;
                        try {
                            written = write();
                        } catch (final IllegalArgumentException ex) {
                            AlertEvent.fireError(McpToolServerEditPresenter.this, ex.getMessage(), null);
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
        updateAuthModeWarning(getView().getAuthModeSelectionBox().getValue());
        getView().setApiKeyFieldsVisible(getView().getAuthModeSelectionBox().getValue() == McpAuthMode.API_KEY);
    }

    @Override
    public void onSetHttpClientConfiguration() {
        httpClientConfigPresenterProvider.get().show(httpClientConfiguration, readOnly, updated -> {
            if (!Objects.equals(httpClientConfiguration, updated)) {
                httpClientConfiguration = updated;
            }
        });
    }

    /**
     * Warns that passing the user's sign-in through needs an external identity provider, if Stroom does not use
     * one (U3).
     */
    private void updateAuthModeWarning(final McpAuthMode authMode) {
        getView().setAuthModeWarning(null);
        if (authMode == McpAuthMode.STROOM_SESSION_TOKEN) {
            uiConfigCache.get(config -> {
                if (config != null && !config.isExternalIdentityProvider()) {
                    getView().setAuthModeWarning("Stroom uses its internal identity provider, so it has no "
                                                 + "sign-in to pass through. This server's tools will be "
                                                 + "unavailable until Stroom uses an external identity provider.");
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
        updateAuthModeWarning(c.getAuthMode());
        if (NullSafe.isNonBlankString(c.getCredentialName())) {
            credentialClient.getCredentialByName(c.getCredentialName(),
                    credential -> getView().getCredentialSelectionBox().setValue(credential), this);
        } else {
            getView().getCredentialSelectionBox().setValue(null);
        }
        getView().setApiKeyHeaderName(NullSafe.nonBlankStringElse(
                c.getApiKeyHeaderName(), McpToolServerConfig.DEFAULT_API_KEY_HEADER_NAME));
        getView().setApiKeyValuePrefix(c.getApiKeyValuePrefix());
        getView().setApiKeyFieldsVisible(c.getAuthMode() == McpAuthMode.API_KEY);
        getView().setAllowedTools(joinPatterns(c.getAllowedTools()));
        getView().setDeniedTools(joinPatterns(c.getDeniedTools()));
        getView().setTrusted(c.isTrusted());
        getView().setToolPolicies(formatPolicies(c.getToolPolicies()));
        getView().setToolGroups(formatGroups(c.getToolGroups()));
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
                .httpClientConfiguration(httpClientConfiguration)
                .allowedTools(splitPatterns(getView().getAllowedTools()))
                .deniedTools(splitPatterns(getView().getDeniedTools()))
                .trusted(getView().isTrusted())
                .toolPolicies(parsePolicies(getView().getToolPolicies()))
                .toolGroups(parseGroups(getView().getToolGroups()))
                .build();
    }

    /**
     * @param text Tool policy rules, one per line, each 'pattern = policy'. The policy can be given by its
     *             display name (e.g. 'Ask every time') or its constant name (e.g. 'ASK_ALWAYS'), in any case.
     * @return The rules, or null if there are none.
     * @throws IllegalArgumentException if a line cannot be understood.
     */
    static List<McpToolPolicyRule> parsePolicies(final String text) {
        if (NullSafe.isBlankString(text)) {
            return null;
        }
        final List<McpToolPolicyRule> rules = new ArrayList<>();
        for (final String rawLine : text.split("\\n")) {
            final String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            final int idx = line.indexOf('=');
            final String pattern = idx > 0
                    ? line.substring(0, idx).trim()
                    : "";
            final McpToolPolicy policy = idx > 0
                    ? parsePolicy(line.substring(idx + 1).trim())
                    : null;
            if (pattern.isEmpty() || policy == null) {
                throw new IllegalArgumentException("The tool policy '" + line + "' is not understood. Each line "
                                                   + "must be 'pattern = policy', where the policy is Allow, Ask, "
                                                   + "Ask every time or Deny.");
            }
            rules.add(new McpToolPolicyRule(pattern, policy));
        }
        return rules.isEmpty()
                ? null
                : rules;
    }

    static String formatPolicies(final List<McpToolPolicyRule> rules) {
        if (NullSafe.isEmptyCollection(rules)) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final McpToolPolicyRule rule : rules) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(rule.getToolPattern()).append(" = ").append(rule.getPolicy().getDisplayValue());
        }
        return sb.toString();
    }

    /**
     * @param text Tool group rules, one per line, each 'pattern = group'. A group of '-' means always offered.
     * @return The rules, or null if there are none.
     * @throws IllegalArgumentException if a line cannot be understood.
     */
    static List<McpToolGroupRule> parseGroups(final String text) {
        if (NullSafe.isBlankString(text)) {
            return null;
        }
        final List<McpToolGroupRule> rules = new ArrayList<>();
        for (final String rawLine : text.split("\\n")) {
            final String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            final int idx = line.indexOf('=');
            final String pattern = idx > 0
                    ? line.substring(0, idx).trim()
                    : "";
            final String group = idx > 0
                    ? line.substring(idx + 1).trim()
                    : "";
            if (pattern.isEmpty() || group.isEmpty()) {
                throw new IllegalArgumentException("The tool group rule '" + line + "' is not understood. Each "
                                                   + "line must be 'pattern = group', where the group is a name, "
                                                   + "or '-' to always offer the tools.");
            }
            rules.add(new McpToolGroupRule(pattern, group));
        }
        return rules.isEmpty()
                ? null
                : rules;
    }

    static String formatGroups(final List<McpToolGroupRule> rules) {
        if (NullSafe.isEmptyCollection(rules)) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final McpToolGroupRule rule : rules) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(rule.getToolPattern()).append(" = ").append(rule.getGroup());
        }
        return sb.toString();
    }

    private static McpToolPolicy parsePolicy(final String text) {
        for (final McpToolPolicy policy : McpToolPolicy.values()) {
            if (policy.getDisplayValue().equalsIgnoreCase(text) || policy.name().equalsIgnoreCase(text)) {
                return policy;
            }
        }
        return null;
    }

    /**
     * @param text Tool name patterns separated by commas, spaces or new lines.
     * @return The patterns, or null if there are none.
     */
    static List<String> splitPatterns(final String text) {
        if (NullSafe.isBlankString(text)) {
            return null;
        }
        final List<String> patterns = new ArrayList<>();
        for (final String part : text.split("[,\\s]+")) {
            if (!part.isEmpty()) {
                patterns.add(part);
            }
        }
        return patterns.isEmpty()
                ? null
                : patterns;
    }

    static String joinPatterns(final List<String> patterns) {
        return NullSafe.isEmptyCollection(patterns)
                ? ""
                : String.join(", ", patterns);
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

        void setApiKeyFieldsVisible(boolean visible);

        /**
         * Shows a warning about the authentication chosen, or hides it.
         *
         * @param warning The warning, or null.
         */
        void setAuthModeWarning(String warning);

        String getAllowedTools();

        void setAllowedTools(String allowedTools);

        String getDeniedTools();

        void setDeniedTools(String deniedTools);

        boolean isTrusted();

        void setTrusted(boolean trusted);

        String getToolPolicies();

        void setToolPolicies(String toolPolicies);

        String getToolGroups();

        void setToolGroups(String toolGroups);
    }
}
