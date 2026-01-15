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

package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.explorer.shared.NodeFlag;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSerialisation {

    @Test
    void testFetchRequest() throws Exception {
        final ExplorerNode explorerNode = ExplorerNode
                .builder()
                .type("test")
                .uuid("test")
                .name("test")
                .tags(Set.of("test"))
                .build();

        final ExplorerTreeFilter explorerTreeFilter = new ExplorerTreeFilter(
                Set.of("t1", "t2"),
                Set.of("t1", "t2"),
                Set.of("t1", "t2"),
                Set.of(NodeFlag.OPEN, NodeFlag.FAVOURITE),
                Set.of(DocumentPermission.USE, DocumentPermission.VIEW),
                "blah",
                true,
                null);

        final FetchExplorerNodesRequest criteria1 = new FetchExplorerNodesRequest(
                Set.of(explorerNode.getUniqueKey()),
                Set.of(explorerNode.getUniqueKey()),
                explorerTreeFilter,
                2,
                Set.of(explorerNode.getUniqueKey()),
                true);

        final ObjectMapper objectMapper = JsonUtil.getMapper();
        final String result1 = objectMapper.writeValueAsString(criteria1);
        System.out.println(result1);
        final FetchExplorerNodesRequest criteria2 = objectMapper.readValue(result1, FetchExplorerNodesRequest.class);
        final String result2 = objectMapper.writerFor(FetchExplorerNodesRequest.class).writeValueAsString(criteria2);
        System.out.println(result2);

//        assertThat(result1).isEqualTo(result2);
        assertThat(criteria2).isEqualTo(criteria1);
    }

    @Test
    void testFetchResponse() throws Exception {
        final ExplorerNode child = ExplorerNode
                .builder()
                .type("test-type")
                .uuid("child-uuid")
                .name("child-name")
                .addTag("test-tags")
                .addNodeFlag(NodeFlag.LEAF)
                .build();

        final ExplorerNode parent = ExplorerNode
                .builder()
                .type("test-type")
                .uuid("parent-uuid")
                .name("parent-name")
                .addTag("test-tags")
                .addNodeFlag(NodeFlag.OPEN)
                .children(List.of(child))
                .build();

        final FetchExplorerNodeResult result1 = new FetchExplorerNodeResult(
                List.of(parent),
                List.of(parent.getUniqueKey()),
                Set.of(parent.getUniqueKey()),
                null);

        final ObjectMapper objectMapper = JsonUtil.getMapper();
        final String string1 = objectMapper.writeValueAsString(result1);
        System.out.println(string1);
        final FetchExplorerNodeResult result2 = objectMapper.readValue(string1, FetchExplorerNodeResult.class);
        final String string2 = objectMapper.writerFor(FetchExplorerNodeResult.class).writeValueAsString(result2);
        System.out.println(string2);

        Response.ok().entity(result1);

//        assertThat(string1).isEqualTo(string2);
        assertThat(result2).isEqualTo(result1);
    }
}
