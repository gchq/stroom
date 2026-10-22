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

package stroom.ai.impl;

import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestToolGrants {

    private final ToolGrants grants = new ToolGrants(Map.of(
            AiToolCategory.SEARCH, AiToolAccess.READ_ONLY,
            AiToolCategory.CONTENT, AiToolAccess.READ_WRITE));

    @Test
    void testACategoryNotGrantedOffersNothing() {
        assertThat(grants.offers(AiToolCategory.PROCESSING, true, true)).isFalse();
        assertThat(grants.allowsCall(AiToolCategory.PROCESSING, true)).isFalse();
        assertThat(grants.access(AiToolCategory.PROCESSING)).isEmpty();
        assertThat(ToolGrants.NONE.offers(AiToolCategory.CORE, true, true)).isFalse();
        assertThat(ToolGrants.NONE.isEmpty()).isTrue();
    }

    @Test
    void testAReadOnlyGrantOffersOnlyWhatCanRead() {
        assertThat(grants.offers(AiToolCategory.SEARCH, true, true)).isTrue();
        // A tool that only changes things isn't offered.
        assertThat(grants.offers(AiToolCategory.SEARCH, false, false)).isFalse();
        // A tool with some calls that only read is, and only those calls are allowed.
        assertThat(grants.offers(AiToolCategory.SEARCH, false, true)).isTrue();
        assertThat(grants.allowsCall(AiToolCategory.SEARCH, true)).isTrue();
        assertThat(grants.allowsCall(AiToolCategory.SEARCH, false)).isFalse();
    }

    @Test
    void testAReadWriteGrantOffersEverything() {
        assertThat(grants.offers(AiToolCategory.CONTENT, false, false)).isTrue();
        assertThat(grants.allowsCall(AiToolCategory.CONTENT, false)).isTrue();
        assertThat(ToolGrants.all().allowsCall(AiToolCategory.ADMINISTRATION, false)).isTrue();
    }

    @Test
    void testTheWiderAccessWins() {
        assertThat(AiToolAccess.READ_ONLY.or(AiToolAccess.READ_WRITE)).isEqualTo(AiToolAccess.READ_WRITE);
        assertThat(AiToolAccess.READ_WRITE.or(AiToolAccess.READ_ONLY)).isEqualTo(AiToolAccess.READ_WRITE);
        assertThat(AiToolAccess.READ_ONLY.or(AiToolAccess.READ_ONLY)).isEqualTo(AiToolAccess.READ_ONLY);
        assertThat(AiToolAccess.READ_ONLY.or(null)).isEqualTo(AiToolAccess.READ_ONLY);
    }
}
