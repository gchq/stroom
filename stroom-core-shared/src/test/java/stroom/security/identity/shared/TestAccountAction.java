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

package stroom.security.identity.shared;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestAccountAction {

    @Test
    void onlyEnablingAndDisablingContradictEachOther() {
        assertThat(AccountAction.ENABLE.getOpposite()).contains(AccountAction.DISABLE);
        assertThat(AccountAction.DISABLE.getOpposite()).contains(AccountAction.ENABLE);

        // An administrator does not lock an account or make one inactive, so there is nothing for these to
        // contradict. Returning empty rather than throwing is what lets the change validation treat them as
        // ordinary actions instead of a programming error.
        assertThat(AccountAction.UNLOCK.getOpposite()).isEmpty();
        assertThat(AccountAction.REACTIVATE.getOpposite()).isEmpty();
    }

    @Test
    void everyOppositeIsMutual() {
        // A one-way opposite would let a change ask for a state and its contradiction, and be accepted
        // depending only on which of the two the validation happened to examine first.
        for (final AccountAction action : AccountAction.values()) {
            final Optional<AccountAction> opposite = action.getOpposite();
            if (opposite.isPresent()) {
                assertThat(opposite.get().getOpposite())
                        .as("%s is the opposite of %s, so the reverse must hold", opposite.get(), action)
                        .contains(action);
            }
        }
    }

    @Test
    void noActionIsItsOwnOpposite() {
        for (final AccountAction action : AccountAction.values()) {
            assertThat(action.getOpposite()).isNotEqualTo(Optional.of(action));
        }
    }
}
