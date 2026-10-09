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

package stroom.dashboard.client.embeddedquery;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.query.client.QueryClient;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestBasicEmbeddedQuerySettingsPresenter {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private BasicEmbeddedQuerySettingsPresenter.BasicEmbeddedQuerySettingsView view;
    private BasicEmbeddedQuerySettingsPresenter presenter;

    @BeforeEach
    void setUp() {
        // The doc selection box makes its parts with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        view = Mockito.mock(BasicEmbeddedQuerySettingsPresenter.BasicEmbeddedQuerySettingsView.class);
        presenter = new BasicEmbeddedQuerySettingsPresenter(
                eventBus,
                view,
                Mockito.mock(DocSelectionBoxPresenter.class),
                () -> null,
                Mockito.mock(QueryClient.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void read_noRefreshInterval() {
        // Regression test: a dashboard saved before the interval had a default has none, which
        // left the box blank so that the settings could not be saved.
        presenter.read(createComponentConfig(new Automate(true, true, null)));

        Mockito.verify(view).setRefreshInterval(Automate.DEFAULT_REFRESH_INTERVAL);
    }

    @Test
    void read_refreshInterval() {
        presenter.read(createComponentConfig(new Automate(true, true, "1m")));

        Mockito.verify(view).setRefreshInterval("1m");
    }

    @Test
    void read_noAutomate() {
        presenter.read(createComponentConfig(null));

        Mockito.verify(view).setRefreshInterval(Automate.DEFAULT_REFRESH_INTERVAL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void validate_blankRefreshInterval(final String interval) {
        // Regression test: a blank interval failed with a TypeError (a NullPointerException in
        // the JVM), which was shown in place of a validation message.
        Mockito.when(view.getRefreshInterval()).thenReturn(interval);

        assertThat(presenter.validate())
                .isFalse();
        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("A query refresh interval must be provided");
    }

    @Test
    void validate_refreshIntervalTooShort() {
        Mockito.when(view.getRefreshInterval()).thenReturn("5s");

        assertThat(presenter.validate())
                .isFalse();
        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("Query refresh interval must be greater than or equal to 10 seconds");
    }

    @Test
    void validate_unreadableRefreshInterval() {
        // Regression test: an interval that couldn't be read was reported as a NullPointerException
        Mockito.when(view.getRefreshInterval()).thenReturn("soon");

        assertThat(presenter.validate())
                .isFalse();
        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("Query refresh interval must be a duration, e.g. 10s, 5m or 1h");
    }

    @Test
    void validate_refreshIntervalTooLong() {
        // Regression test: 25 days or more wrapped round to a negative number of milliseconds, and
        // was refused as being under 10 seconds
        Mockito.when(view.getRefreshInterval()).thenReturn("30d");

        assertThat(presenter.validate())
                .isFalse();
        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("Query refresh interval must be 24 days or less");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10s", "1m"})
    void validate_validRefreshInterval(final String interval) {
        Mockito.when(view.getRefreshInterval()).thenReturn(interval);

        assertThat(presenter.validate())
                .isTrue();
        assertThat(alerts)
                .isEmpty();
    }

    private ComponentConfig createComponentConfig(final Automate automate) {
        return ComponentConfig.builder()
                .id("component-id")
                .name("Query")
                .settings(EmbeddedQueryComponentSettings.builder()
                        .automate(automate)
                        .build())
                .build();
    }
}
