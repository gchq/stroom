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

package stroom.test.common.util.test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ContentPackZip {

    @JsonProperty
    private final String url;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String version;

    @JsonCreator
    public ContentPackZip(@JsonProperty("url") final String url,
                          @JsonProperty("name") final String name,
                          @JsonProperty("version") final String version) {
        this.url = url;
        this.name = name;
        this.version = version;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String toFileName() {
        return name + "-v" + version + ".zip";
    }

    @Override
    public String toString() {
        return name + "-v" + version;
    }
}
