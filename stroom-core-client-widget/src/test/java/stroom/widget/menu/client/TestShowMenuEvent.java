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

package stroom.widget.menu.client;

import com.google.gwt.dom.client.Element;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestShowMenuEvent {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowMenuEvent> shown = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus.addHandler(ShowMenuEvent.getType(), shown::add);
    }

    @Test
    void name_defaultsToNull() {
        ShowMenuEvent.builder()
                .items(List.of())
                .fire(eventBus::fireEvent);

        assertThat(shown).singleElement()
                .satisfies(event -> {
                    assertThat(event.getName()).isNull();
                    assertThat(event.getOpener()).isNull();
                    assertThat(event.getAutoHidePartners()).isNull();
                });
    }

    @Test
    void name_isCarried() {
        ShowMenuEvent.builder()
                .name("Actions for Feed 'MY_FEED'")
                .items(List.of())
                .fire(eventBus::fireEvent);

        assertThat(shown).singleElement()
                .extracting(ShowMenuEvent::getName)
                .isEqualTo("Actions for Feed 'MY_FEED'");
    }

    @Test
    void name_nullClearsAnEarlierName() {
        ShowMenuEvent.builder()
                .name("Main")
                .name(null)
                .items(List.of())
                .fire(eventBus::fireEvent);

        assertThat(shown).singleElement()
                .extracting(ShowMenuEvent::getName)
                .isNull();
    }

    @Test
    void opener_isAnAutoHidePartner() {
        final Element opener = Mockito.mock(Element.class);

        ShowMenuEvent.builder()
                .items(List.of())
                .opener(opener)
                .fire(eventBus::fireEvent);

        assertThat(shown).singleElement()
                .satisfies(event -> {
                    assertThat(event.getOpener()).isSameAs(opener);
                    assertThat(event.getName()).isNull();
                    assertThat(event.getAutoHidePartners()).containsExactly(opener);
                });
    }

    @Test
    void nameAndOpener_bothCarried() {
        final Element opener = Mockito.mock(Element.class);
        final Element partner = Mockito.mock(Element.class);

        ShowMenuEvent.builder()
                .name("Add Component")
                .items(List.of())
                .addAutoHidePartner(partner)
                .opener(opener)
                .fire(eventBus::fireEvent);

        assertThat(shown).singleElement()
                .satisfies(event -> {
                    assertThat(event.getName()).isEqualTo("Add Component");
                    assertThat(event.getOpener()).isSameAs(opener);
                    assertThat(event.getAutoHidePartners()).containsExactly(partner, opener);
                });
    }

    @Test
    void nullOpener_addsNoAutoHidePartner() {
        ShowMenuEvent.builder()
                .name("Main")
                .items(List.of())
                .opener(null)
                .fire(eventBus::fireEvent);

        assertThat(shown).singleElement()
                .satisfies(event -> {
                    assertThat(event.getOpener()).isNull();
                    assertThat(event.getAutoHidePartners()).isNull();
                });
    }
}
