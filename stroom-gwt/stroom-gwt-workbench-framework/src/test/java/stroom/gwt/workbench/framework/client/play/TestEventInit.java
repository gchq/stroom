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


package stroom.gwt.workbench.framework.client.play;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventInit {

    @Test
    void testDescribe() {
        assertThat(EventInit.create().describe()).isEqualTo("{}");
        assertThat(EventInit.create().shiftKey().altKey().metaKey().detail(2).buttons(1).bubbles(false)
                .code("KeyU").keyCode(85).describe())
                .isEqualTo("{ buttons: 1, detail: 2, shiftKey: true, altKey: true, metaKey: true, "
                           + "code: \"KeyU\", keyCode: 85, bubbles: false }");
        // The last position set wins
        final Play play = new Play();
        assertThat(EventInit.create().atCentreOf(play.body()).at(1, 2).describe())
                .isEqualTo("{ clientX: 1, clientY: 2 }");
    }

    @Test
    void testValues() {
        final Play play = new Play();
        final Query body = play.body();
        final EventInit init = EventInit.create().relativeTo(body, 3, 4).button(2).key("a").ctrlKey().shiftKey();
        assertThat(init.getReference()).isSameAs(body);
        assertThat(init.getOffsetX()).isEqualTo(3.0);
        assertThat(init.getOffsetY()).isEqualTo(4.0);
        assertThat(init.getButton()).isEqualTo(2);
        assertThat(init.getKey()).isEqualTo("a");
        assertThat(init.getModifiers()).isEqualTo(Keys.CONTROL | Keys.SHIFT);
        assertThat(init.getClientX()).isNull();
        assertThat(init.getKeyCode()).isNull();
        assertThat(init.getBubbles()).isNull();
    }
}
