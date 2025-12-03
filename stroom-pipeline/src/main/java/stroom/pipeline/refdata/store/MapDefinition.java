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

package stroom.pipeline.refdata.store;

import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class MapDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapDefinition.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(MapDefinition.class);

    @JsonProperty
    private final RefStreamDefinition refStreamDefinition;
    @JsonProperty
    private final String mapName;
    @JsonIgnore
    private final int hashCode;

    @JsonCreator
    public MapDefinition(@JsonProperty("refStreamDefinition") final RefStreamDefinition refStreamDefinition,
                         @JsonProperty("mapName") final String mapName) {
        this.refStreamDefinition = refStreamDefinition;
        this.mapName = mapName;
        this.hashCode = buildHashCode();
    }

    public MapDefinition(final RefStreamDefinition refStreamDefinition) {
        this.refStreamDefinition = refStreamDefinition;
        this.mapName = null;
        // pre-compute the hash
        this.hashCode = buildHashCode();
    }

    DocRef getPipelineDocRef() {
        return refStreamDefinition.getPipelineDocRef();
    }

    String getPipelineVersion() {
        return refStreamDefinition.getPipelineVersion();
    }

    long getStreamId() {
        return refStreamDefinition.getStreamId();
    }

    public String getMapName() {
        return mapName;
    }

    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MapDefinition that = (MapDefinition) o;
        return Objects.equals(refStreamDefinition, that.refStreamDefinition) &&
               Objects.equals(mapName, that.mapName);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int buildHashCode() {
        return Objects.hash(refStreamDefinition, mapName);
    }

    @Override
    public String toString() {
        return "MapDefinition{" +
               "pipelineDocRef=" + refStreamDefinition.getPipelineDocRef() +
               ", pipelineVer=" + refStreamDefinition.getPipelineVersion() +
               ", streamId=" + refStreamDefinition.getStreamId() +
               ", partIndex=" + refStreamDefinition.getPartIndex() +
               ", mapName='" + mapName + '\'' +
               '}';
    }
}
