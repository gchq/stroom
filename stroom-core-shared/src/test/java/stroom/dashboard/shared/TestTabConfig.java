/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTabConfig {

    @Test
    void test1() {
        final TabConfig tabConfig = new TabConfig("123", true);
        final TabConfig tabConfig2 = TestUtil.testSerialisation(tabConfig, TabConfig.class);
        assertThat(tabConfig2.isVisible())
                .isEqualTo(tabConfig.isVisible());
    }

    @Test
    void test2() {
        final TabConfig tabConfig = new TabConfig("123", true);
        tabConfig.setVisible(true);
        final TabConfig tabConfig2 = TestUtil.testSerialisation(tabConfig, TabConfig.class);
        assertThat(tabConfig2.isVisible())
                .isEqualTo(tabConfig.isVisible());
    }

    @Test
    void test3() {
        final TabConfig tabConfig = new TabConfig("123", false);
        final TabConfig tabConfig2 = TestUtil.testSerialisation(tabConfig, TabConfig.class);
        assertThat(tabConfig2.isVisible())
                .isEqualTo(tabConfig.isVisible());
    }

    @Test
    void test4() {
        final TabConfig tabConfig = new TabConfig("123", false);
        tabConfig.setVisible(false);
        final TabConfig tabConfig2 = TestUtil.testSerialisation(tabConfig, TabConfig.class);
        assertThat(tabConfig2.isVisible())
                .isEqualTo(tabConfig.isVisible());
    }

    @Test
    void test5() {
        final TabConfig tabConfig = new TabConfig("123", null);
        final TabConfig tabConfig2 = TestUtil.testSerialisation(tabConfig, TabConfig.class);
        assertThat(tabConfig2.isVisible())
                .isEqualTo(tabConfig.isVisible())
                .isEqualTo(true);
    }

    @Test
    void test6() {
        final TabConfig tabConfig = new TabConfig("123", null);
        assertThat(tabConfig.isVisible())
                .isTrue();
        tabConfig.setVisible(false);
        assertThat(tabConfig.isVisible())
                .isFalse();
        tabConfig.setVisible(true);
        assertThat(tabConfig.isVisible())
                .isTrue();
    }
}
