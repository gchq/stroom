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

package stroom.widget.util.client;

import stroom.widget.util.client.Console.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestConsole {

    private final Level levelBefore = Console.getLevel();

    /// Puts back the level a test changed, which every test shares.
    @AfterEach
    void restoreLevel() {
        Console.setLevel(levelBefore);
    }

    /// Super Dev Mode logs everything, and any other build warnings and errors only.
    @Test
    void defaultLevel() {
        assertThat(Console.defaultLevel(true)).isEqualTo(Level.DEBUG);
        assertThat(Console.defaultLevel(false)).isEqualTo(Level.WARN);
    }

    /// A level is logged at its own threshold and less severe ones.
    @Test
    void isLoggedAt() {
        assertThat(Level.ERROR.isLoggedAt(Level.WARN)).isTrue();
        assertThat(Level.WARN.isLoggedAt(Level.WARN)).isTrue();
        assertThat(Level.INFO.isLoggedAt(Level.WARN)).isFalse();
        assertThat(Level.DEBUG.isLoggedAt(Level.WARN)).isFalse();
        assertThat(Level.DEBUG.isLoggedAt(Level.DEBUG)).isTrue();
    }

    /// A level is named in any case, with or without space round it.
    @Test
    void parse() {
        assertThat(Level.parse("debug")).isEqualTo(Level.DEBUG);
        assertThat(Level.parse("Info")).isEqualTo(Level.INFO);
        assertThat(Level.parse(" WARN ")).isEqualTo(Level.WARN);
        assertThat(Level.parse("error")).isEqualTo(Level.ERROR);
    }

    /// What isn't a level's name is no level.
    @Test
    void parseUnknown() {
        assertThat(Level.parse(null)).isNull();
        assertThat(Level.parse("")).isNull();
        assertThat(Level.parse("FINE")).isNull();
    }

    /// The URL parameter sets the level, and one missing or unknown leaves it.
    @Test
    void setLevelFromParameter() {
        Console.setLevelFromParameter("error");
        assertThat(Console.getLevel()).isEqualTo(Level.ERROR);
        Console.setLevelFromParameter(null);
        assertThat(Console.getLevel()).isEqualTo(Level.ERROR);
        Console.setLevelFromParameter("loud");
        assertThat(Console.getLevel()).isEqualTo(Level.ERROR);
    }
}
