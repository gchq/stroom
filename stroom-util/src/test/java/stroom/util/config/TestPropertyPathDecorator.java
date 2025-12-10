/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.config;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestPropertyPathDecorator {

    /**
     * Decorate a tree of config objects with path information and make sure it is
     * all correct
     */
    @Test
    void test() {
        final RootConfig rootConfig = new RootConfig();
        PropertyPathDecorator.decoratePaths(rootConfig, PropertyPath.fromParts("root"));

        Assertions.assertThat(rootConfig.getBasePathStr())
                .isEqualTo("root");
        Assertions.assertThat(rootConfig.getLevel())
                .isEqualTo("root");
        Assertions.assertThat(rootConfig.getFullPathStr("level"))
                .isEqualTo("root.level");

        Assertions.assertThat(rootConfig.getChild().getBasePathStr())
                .isEqualTo("root.child");
        Assertions.assertThat(rootConfig.getChild().getLevel())
                .isEqualTo("child");
        Assertions.assertThat(rootConfig.getChild().getFullPathStr("level"))
                .isEqualTo("root.child.level");

        Assertions.assertThat(rootConfig.getChild().getGrandchild().getBasePathStr())
                .isEqualTo("root.child.grandchild");
        Assertions.assertThat(rootConfig.getChild().getGrandchild().getLevel())
                .isEqualTo("grandchild");
        Assertions.assertThat(rootConfig.getChild().getGrandchild().getFullPathStr("level"))
                .isEqualTo("root.child.grandchild.level");
    }

    private static class RootConfig extends AbstractConfig {

        private final String level;
        private final ChildConfig child;

        public RootConfig() {
            level = "root";
            child = new ChildConfig();
        }

        public RootConfig(@JsonProperty("level") final String level,
                          @JsonProperty("child") final ChildConfig child) {
            this.level = level;
            this.child = child;
        }

        public String getLevel() {
            return level;
        }

        public ChildConfig getChild() {
            return child;
        }
    }

    private static class ChildConfig extends AbstractConfig {

        private final String level;
        private final GrandchildConfig grandChild;

        public ChildConfig() {
            level = "child";
            grandChild = new GrandchildConfig();
        }

        private ChildConfig(@JsonProperty("level") final String level,
                            @JsonProperty("grandchild") final GrandchildConfig grandChild) {
            this.level = level;
            this.grandChild = grandChild;
        }

        public String getLevel() {
            return level;
        }

        public GrandchildConfig getGrandchild() {
            return grandChild;
        }
    }

    private static class GrandchildConfig extends AbstractConfig {

        private final String level;

        private GrandchildConfig(@JsonProperty("level") final String level) {
            this.level = level;
        }

        public GrandchildConfig() {
            level = "grandchild";
        }

        public String getLevel() {
            return level;
        }
    }
}
