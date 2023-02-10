package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeState;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSerialisation {

    @Test
    void testFetchRequest() throws Exception {
        final ExplorerNode explorerNode = ExplorerNode
                .builder()
                .type("test")
                .uuid("test")
                .name("test")
                .tags("test")
                .build();

        final ExplorerTreeFilter explorerTreeFilter = new ExplorerTreeFilter(
                Set.of("t1", "t2"),
                Set.of("t1", "t2"),
                Set.of("t1", "t2"),
                Set.of("p1", "p2"),
                "blah",
                true);

        final FindExplorerNodeCriteria criteria1 = new FindExplorerNodeCriteria(
                Set.of(explorerNode.getUniqueKey()),
                Set.of(explorerNode.getUniqueKey()),
                explorerTreeFilter,
                2,
                Set.of(explorerNode.getUniqueKey()));

        final ObjectMapper objectMapper = JsonUtil.getMapper();
        final String result1 = objectMapper.writeValueAsString(criteria1);
        System.out.println(result1);
        final FindExplorerNodeCriteria criteria2 = objectMapper.readValue(result1, FindExplorerNodeCriteria.class);
        final String result2 = objectMapper.writerFor(FindExplorerNodeCriteria.class).writeValueAsString(criteria2);
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
                .tags("test-tags")
                .nodeState(NodeState.LEAF)
                .build();

        final ExplorerNode parent = ExplorerNode
                .builder()
                .type("test-type")
                .uuid("parent-uuid")
                .name("parent-name")
                .tags("test-tags")
                .nodeState(NodeState.OPEN)
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
