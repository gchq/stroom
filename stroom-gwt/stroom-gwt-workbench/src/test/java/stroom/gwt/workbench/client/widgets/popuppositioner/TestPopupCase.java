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


package stroom.gwt.workbench.client.widgets.popuppositioner;

import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPopupCase {

    @Test
    void testSelectorsAreScopedToTheContainer() {
        final PopupCase popupCase = PopupCase.of("My case", popups -> null)
                .opener("button.open")
                .partners("button.open", "button.open svg")
                .panel(".popup");
        assertThat(popupCase.getOpener()).isEqualTo("[data-case='My case'] button.open");
        assertThat(popupCase.getPartners()).containsExactly(
                "[data-case='My case'] button.open",
                "[data-case='My case'] button.open svg");
        // The panel is on the page's body, so it isn't scoped
        assertThat(popupCase.getPanel()).isEqualTo(".popup");
        assertThat(popupCase.within(".x")).isEqualTo("[data-case='My case'] .x");
    }

    @Test
    void testDefaults() {
        final PopupCase popupCase = PopupCase.of("Case", popups -> null);
        // The contract, unless a case says GWT differs
        assertThat(popupCase.isPartnersExempt()).isTrue();
        assertThat(popupCase.isTriggerToggles()).isTrue();
        assertThat(popupCase.isEscapeCloses()).isTrue();
        assertThat(popupCase.isReactTriggerToggles()).isTrue();
        assertThat(popupCase.isReactEscapeClosesViaHook()).isTrue();
        assertThat(popupCase.getPlacement()).isNull();
        assertThat(popupCase.getPartners()).isEmpty();
    }

    @Test
    void testFlags() {
        final PopupCase popupCase = PopupCase.of("Case", popups -> null)
                .partnersExempt(false)
                .triggerToggles(false)
                .escapeCloses(false)
                .react(false, false)
                .placement("input", PopupLocation.BELOW, 4, "call site");
        assertThat(popupCase.isPartnersExempt()).isFalse();
        assertThat(popupCase.isTriggerToggles()).isFalse();
        assertThat(popupCase.isEscapeCloses()).isFalse();
        assertThat(popupCase.isReactTriggerToggles()).isFalse();
        assertThat(popupCase.isReactEscapeClosesViaHook()).isFalse();
        assertThat(popupCase.getPlacement().getAnchor()).isEqualTo("input");
        assertThat(popupCase.getPlacement().getLocation()).isEqualTo(PopupLocation.BELOW);
        assertThat(popupCase.getPlacement().getShadow()).isEqualTo(4);
        assertThat(popupCase.getPlacement().getGwt()).isEqualTo("call site");
    }
}
