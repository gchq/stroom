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

package stroom.config.global.impl;

import stroom.annotation.impl.AnnotationState;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigTarget;
import stroom.config.global.shared.SetConfigValueRequest;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.explorer.impl.ExplorerConfig;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.impl.StroomReceiptPolicyConfig;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.StroomOpenIdConfig;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.ui.config.shared.ReportUiDefaultConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.PropertyPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the audit logging of {@link GlobalConfigResourceImpl#setConfigValue}.
 * <p>
 * This has the same defect as the one reported in issue #5800 for
 * {@code NodeGroupResourceImpl.updateNodeGroupState}. The method name makes the auto logger treat
 * it as an update, but the {@code Boolean} response cannot be used as the 'after' and
 * {@link SetConfigValueRequest} carries no id for it to fetch a before/after with, so no audit
 * event was produced for a change to a global config property.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestGlobalConfigResourceImplSetConfigValueAudit {

    private static final String PROPERTY_NAME = "defaultDashboardUuid";
    private static final String TYPE_ID = "GlobalConfigResourceImpl.setConfigValue";

    @Mock
    private GlobalConfigService mockGlobalConfigService;
    @Mock
    private DocumentEventLog mockDocumentEventLog;

    private GlobalConfigResourceImpl resource;
    private PropertyPath propertyPath;

    @BeforeEach
    void setUp() {
        resource = buildResource();
        propertyPath = new AnalyticUiDefaultConfig().getFullPath(PROPERTY_NAME);
    }

    @Test
    void testSetConfigValue_logsThePropertyEitherSideOfTheChange() {
        final ConfigProperty before = property("before-value");
        final ConfigProperty after = property("after-value");
        when(mockGlobalConfigService.fetch(eq(propertyPath)))
                .thenReturn(Optional.of(before), Optional.of(after));

        final Boolean result = resource.setConfigValue(request("after-value"));

        assertThat(result).isTrue();
        verify(mockDocumentEventLog).update(eq(before), eq(after), eq(TYPE_ID), any(String.class), eq(null));
    }

    @Test
    void testSetConfigValue_alwaysLogsSomethingForBeforeOrAfter() {
        // Regression guard. DocumentEventLogImpl drops an update event where both before and after
        // are null, which is how a change to a config property went unaudited.
        when(mockGlobalConfigService.fetch(any(PropertyPath.class)))
                .thenReturn(Optional.empty());

        resource.setConfigValue(request("some-value"));

        assertBeforeOrAfterHasAValue();
    }

    @Test
    void testSetConfigValue_fallsBackToTheRequestWhenThePropertyCannotBeRead() {
        when(mockGlobalConfigService.fetch(any(PropertyPath.class)))
                .thenThrow(new RuntimeException("no such property"));
        final SetConfigValueRequest request = request("some-value");

        resource.setConfigValue(request);

        verify(mockDocumentEventLog).update(eq(null), eq(request), eq(TYPE_ID), any(String.class), eq(null));
    }

    @Test
    void testSetConfigValue_logsTheFailureAndRethrows() {
        final ConfigProperty before = property("before-value");
        when(mockGlobalConfigService.fetch(eq(propertyPath)))
                .thenReturn(Optional.of(before));
        final RuntimeException expected = new RuntimeException("set failed");
        doThrow(expected)
                .when(mockGlobalConfigService)
                .setString(any(), any(), any());

        assertThatThrownBy(() -> resource.setConfigValue(request("after-value")))
                .isSameAs(expected);

        verify(mockDocumentEventLog).update(eq(before), any(), eq(TYPE_ID), any(String.class), eq(expected));
        assertBeforeOrAfterHasAValue();
    }

    @Test
    void testSetConfigValue_failureDoesNotPresentTheRequestAsTheAfter() {
        // The requested value was never reached, so it must not appear in the event's 'after'.
        when(mockGlobalConfigService.fetch(any(PropertyPath.class)))
                .thenReturn(Optional.empty());
        final RuntimeException expected = new RuntimeException("set failed");
        doThrow(expected)
                .when(mockGlobalConfigService)
                .setString(any(), any(), any());
        final SetConfigValueRequest request = request("never-applied");

        assertThatThrownBy(() -> resource.setConfigValue(request))
                .isSameAs(expected);

        verify(mockDocumentEventLog).update(eq(request), eq(null), eq(TYPE_ID), any(String.class), eq(expected));
    }

    private void assertBeforeOrAfterHasAValue() {
        final ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        final ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mockDocumentEventLog).update(
                beforeCaptor.capture(),
                afterCaptor.capture(),
                any(String.class),
                any(String.class),
                any());

        assertThat(beforeCaptor.getValue() != null || afterCaptor.getValue() != null)
                .describedAs("Either before or after must have a value, or no audit event is logged")
                .isTrue();
    }

    private static ConfigProperty property(final String value) {
        return ConfigProperty.builder()
                .name(PropertyPath.fromParts("analyticUiDefaults", PROPERTY_NAME))
                .databaseOverrideValue(value)
                .build();
    }

    private static SetConfigValueRequest request(final String value) {
        return new SetConfigValueRequest(
                ConfigTarget.ANALYTIC_UI_DEFAULT,
                PROPERTY_NAME,
                null,
                value);
    }

    private GlobalConfigResourceImpl buildResource() {
        return new GlobalConfigResourceImpl(
                MockStroomEventLoggingService::new,
                () -> mockDocumentEventLog,
                () -> mockGlobalConfigService,
                () -> mock(NodeService.class),
                UiConfig::new,
                () -> mock(NodeInfo.class),
                StroomOpenIdConfig::new,
                ExplorerConfig::new,
                AuthenticationConfig::new,
                StroomReceiptPolicyConfig::new,
                ReceiveDataConfig::new,
                AnnotationState::new,
                AnalyticUiDefaultConfig::new,
                ReportUiDefaultConfig::new);
    }
}
