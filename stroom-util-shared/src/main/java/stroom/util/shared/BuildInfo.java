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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class BuildInfo {

    @JsonProperty
    private final long upTime;
    @JsonProperty
    private final long buildTime;
    @JsonProperty
    private final String buildVersion;

    @JsonCreator
    public BuildInfo(@JsonProperty("upTime") final long upTime,
                     @JsonProperty("buildVersion") final String buildVersion,
                     @JsonProperty("buildTime") final long buildTime) {
        this.upTime = upTime;
        this.buildVersion = buildVersion;
        this.buildTime = buildTime;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public long getBuildTime() {
        return buildTime;
    }

    public long getUpTime() {
        return upTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final BuildInfo buildInfo)) {
            return false;
        }
        return upTime == buildInfo.upTime
               && buildTime == buildInfo.buildTime
               && Objects.equals(buildVersion, buildInfo.buildVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upTime, buildTime, buildVersion);
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
               "upTime='" + upTime + '\'' +
               ", buildTime='" + buildTime + '\'' +
               ", buildVersion='" + buildVersion + '\'' +
               '}';
    }
}
