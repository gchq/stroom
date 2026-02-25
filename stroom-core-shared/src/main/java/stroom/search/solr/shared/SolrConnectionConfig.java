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

package stroom.search.solr.shared;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"useZk", "instanceType", "solrUrls", "zkHosts", "zkPath"})
@JsonInclude(Include.NON_NULL)
public class SolrConnectionConfig implements Serializable {

    @JsonProperty
    private final InstanceType instanceType;
    @JsonProperty
    private final boolean useZk;
    @JsonProperty
    private final List<String> solrUrls;
    @JsonProperty
    private final List<String> zkHosts;
    @JsonProperty
    private final String zkPath;

    @JsonCreator
    public SolrConnectionConfig(@JsonProperty("instanceType") final InstanceType instanceType,
                                @JsonProperty("useZk") final boolean useZk,
                                @JsonProperty("solrUrls") final List<String> solrUrls,
                                @JsonProperty("zkHosts") final List<String> zkHosts,
                                @JsonProperty("zkPath") final String zkPath) {
        this.instanceType = instanceType;
        this.useZk = useZk;
        this.solrUrls = solrUrls;
        this.zkHosts = zkHosts;
        this.zkPath = zkPath;
    }

    public InstanceType getInstanceType() {
        return instanceType;
    }

    public boolean isUseZk() {
        return useZk;
    }

    public List<String> getSolrUrls() {
        return solrUrls;
    }

    public List<String> getZkHosts() {
        return zkHosts;
    }

    public String getZkPath() {
        return zkPath;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SolrConnectionConfig that = (SolrConnectionConfig) o;
        return useZk == that.useZk &&
               instanceType == that.instanceType &&
               Objects.equals(solrUrls, that.solrUrls) &&
               Objects.equals(zkHosts, that.zkHosts) &&
               Objects.equals(zkPath, that.zkPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceType, useZk, solrUrls, zkHosts, zkPath);
    }

    @Override
    public String toString() {
        return "SolrConnectionConfig{" +
               "instanceType=" + instanceType +
               ", useZk=" + useZk +
               ", solrUrls=" + solrUrls +
               ", zkHosts=" + zkHosts +
               ", zkPath='" + zkPath + '\'' +
               '}';
    }

    public enum InstanceType implements HasDisplayValue {
        SINGLE_NOOE("Single Node"),
        SOLR_CLOUD("Solr Cloud");

        private final String displayValue;

        InstanceType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private InstanceType instanceType;
        private boolean useZk;
        private List<String> solrUrls;
        private List<String> zkHosts;
        private String zkPath;

        private Builder() {
        }

        private Builder(final SolrConnectionConfig config) {
            this.instanceType = config.instanceType;
            this.useZk = config.useZk;
            this.solrUrls = config.solrUrls;
            this.zkHosts = config.zkHosts;
            this.zkPath = config.zkPath;
        }

        public Builder instanceType(final InstanceType instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder useZk(final boolean useZk) {
            this.useZk = useZk;
            return this;
        }

        public Builder solrUrls(final List<String> solrUrls) {
            this.solrUrls = solrUrls;
            return this;
        }

        public Builder zkHosts(final List<String> zkHosts) {
            this.zkHosts = zkHosts;
            return this;
        }

        public Builder zkPath(final String zkPath) {
            this.zkPath = zkPath;
            return this;
        }

        public SolrConnectionConfig build() {
            return new SolrConnectionConfig(
                    instanceType,
                    useZk,
                    solrUrls,
                    zkHosts,
                    zkPath);
        }
    }
}
