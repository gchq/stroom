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

package stroom.proxy.app.pipeline;

import java.util.Arrays;

/**
 * Logical stage names for the reference-message proxy pipeline.
 */
public enum PipelineStageName {
    RECEIVE("receive"),
    SPLIT_ZIP("splitZip"),
    PRE_AGGREGATE("preAggregate"),
    AGGREGATE("aggregate"),
    FORWARD("forward");

    private final String configName;

    PipelineStageName(final String configName) {
        this.configName = configName;
    }

    /**
     * @return The stage name as used in pipeline configuration.
     */
    public String getConfigName() {
        return configName;
    }

    public static PipelineStageName fromConfigName(final String configName) {
        if (configName == null || configName.isBlank()) {
            throw new IllegalArgumentException("configName must not be blank");
        }

        return Arrays.stream(values())
                .filter(stageName -> stageName.configName.equals(configName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown pipeline stage name: " + configName));
    }
}
