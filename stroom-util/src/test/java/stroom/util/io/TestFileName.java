/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.io;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileName {

    @Test
    void testSimple() {
        assertThat(FileName.parse("001.dat").getBaseName()).isEqualTo("001");
        assertThat(FileName.parse("001.dat").getExtension()).isEqualTo(".dat");
        assertThat(FileName.parse("001.001.dat").getBaseName()).isEqualTo("001.001");
        assertThat(FileName.parse("001.001.dat").getExtension()).isEqualTo(".dat");
        assertThat(FileName.parse("001").getBaseName()).isEqualTo("001");
        assertThat(FileName.parse("001").getExtension()).isEqualTo("");
        assertThat(FileName.parse(".dat").getBaseName()).isEqualTo("");
        assertThat(FileName.parse(".dat").getExtension()).isEqualTo(".dat");
    }
}
