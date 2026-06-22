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

package stroom.explorer.shared;

import stroom.docref.DocAuditEntry;
import stroom.util.shared.ResultPage;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ExplorerNodeInfo {

    @JsonProperty
    private final ExplorerNode explorerNode;
    @JsonProperty
    private final ResultPage<DocAuditEntry> auditEntries;

    @JsonCreator
    public ExplorerNodeInfo(@JsonProperty("explorerNode") final ExplorerNode explorerNode,
                            @JsonProperty("auditEntries") final ResultPage<DocAuditEntry> auditEntries) {
        this.explorerNode = Objects.requireNonNull(explorerNode);
        this.auditEntries = Objects.requireNonNull(auditEntries);
    }

    @SerialisationTestConstructor
    private ExplorerNodeInfo() {
        this.explorerNode = ExplorerNode.builder().build();
        this.auditEntries = ResultPage.empty();
    }

    public ExplorerNode getExplorerNode() {
        return explorerNode;
    }

    public ResultPage<DocAuditEntry> getAuditEntries() {
        return auditEntries;
    }

    @Override
    public String toString() {
        return "ExplorerNodeInfo{" +
               "explorerNode=" + explorerNode +
               ", auditEntries=" + auditEntries +
               '}';
    }
}
