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

package stroom.cell.client;

import com.google.gwt.cell.client.Cell.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestActionMenuCell {

    @Test
    void getMenuName_givenName_isKept() {
        assertThat(ActionMenuCell.getMenuName("Actions for user 'jbloggs'", new Context(4, 0, null)))
                .isEqualTo("Actions for user 'jbloggs'");
    }

    @Test
    void getMenuName_noName_isTheRowNumberFromOne() {
        assertThat(ActionMenuCell.getMenuName(null, new Context(0, 0, null)))
                .isEqualTo("Actions for row 1");
        assertThat(ActionMenuCell.getMenuName(null, new Context(4, 2, null)))
                .isEqualTo("Actions for row 5");
    }

    @Test
    void getMenuName_blankName_isTheRowNumber() {
        assertThat(ActionMenuCell.getMenuName("", new Context(2, 0, null)))
                .isEqualTo("Actions for row 3");
        assertThat(ActionMenuCell.getMenuName("  ", new Context(2, 0, null)))
                .isEqualTo("Actions for row 3");
    }

    @Test
    void getMenuName_noContext_isRowOne() {
        assertThat(ActionMenuCell.getMenuName(null, null))
                .isEqualTo("Actions for row 1");
    }
}
