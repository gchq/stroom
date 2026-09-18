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

package stroom.cluster.lock.impl.dao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code deleteLocks} turns a caller's prefix into a LIKE pattern, so a prefix containing a wildcard
 * would delete locks it does not name.
 */
class TestClusterLockLikeEscaping {

    @Test
    void leavesAnOrdinaryPrefixAlone() {
        final String prefix = "planb-merge-6f1e2b3c-4d5e-6f70-8192-a3b4c5d6e7f8-";
        assertThat(DbClusterLock.escapeLikeLiteral(prefix)).isEqualTo(prefix);
    }

    @Test
    void escapesWildcards() {
        assertThat(DbClusterLock.escapeLikeLiteral("planb-merge-%")).isEqualTo("planb-merge-!%");
        assertThat(DbClusterLock.escapeLikeLiteral("planb_merge")).isEqualTo("planb!_merge");
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        // Otherwise a literal '!' would turn the character after it into an escape sequence.
        assertThat(DbClusterLock.escapeLikeLiteral("a!_b")).isEqualTo("a!!!_b");
    }
}
