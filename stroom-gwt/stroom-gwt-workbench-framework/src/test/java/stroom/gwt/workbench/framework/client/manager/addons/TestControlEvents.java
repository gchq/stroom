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

package stroom.gwt.workbench.framework.client.manager.addons;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestControlEvents {

    @ParameterizedTest
    @CsvSource(value = {
            // Regression test: these used to be handled on both events, so changed twice
            "SELECT,,true",
            "INPUT,radio,true",
            "INPUT,checkbox,true",
            "input,CHECKBOX,true",
            // Handled as the user types or drags
            "INPUT,text,false",
            "INPUT,number,false",
            "INPUT,range,false",
            "INPUT,datetime-local,false",
            "INPUT,,false",
            "TEXTAREA,,false",
            "BUTTON,,false",
    })
    void testIsHandledOnChange(final String tagName, final String inputType, final boolean expected) {
        assertThat(ControlEvents.isHandledOnChange(tagName, inputType)).isEqualTo(expected);
    }
}
