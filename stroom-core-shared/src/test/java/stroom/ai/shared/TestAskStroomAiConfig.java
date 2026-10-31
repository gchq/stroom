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

package stroom.ai.shared;

import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.ai.shared.AskStroomAiConfig.DockType;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAskStroomAiConfig {

    @Test
    void settingsSavedWithoutADockAreDockedOnTheRight() {
        // Otherwise the chat opened in a dialog.
        final AskStroomAiConfig config = JsonUtil.readValue("{}", AskStroomAiConfig.class);

        assertThat(config.getDockType()).isEqualTo(DockType.DOCK);
        assertThat(config.getDockLocation()).isEqualTo(DockLocation.RIGHT);
    }

    @Test
    void aChosenDockIsKept() {
        final AskStroomAiConfig config = JsonUtil.readValue(
                "{\"dockType\":\"DIALOG\",\"dockLocation\":\"LEFT\"}", AskStroomAiConfig.class);

        assertThat(config.getDockType()).isEqualTo(DockType.DIALOG);
        assertThat(config.getDockLocation()).isEqualTo(DockLocation.LEFT);
    }

    @Test
    void floatIsDocked() {
        assertThat(JsonUtil.readValue("{\"dockType\":\"FLOAT\"}", AskStroomAiConfig.class).getDockType())
                .isEqualTo(DockType.DOCK);
    }
}
