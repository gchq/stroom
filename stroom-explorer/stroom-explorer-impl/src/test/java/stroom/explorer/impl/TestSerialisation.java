package stroom.explorer.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeState;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.util.json.JsonUtil;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSerialisation {
    @Test
    void testFetchRequest() throws Exception {
        final ExplorerNode explorerNode = new ExplorerNode("test", "test", "test", "test");

        final ExplorerTreeFilter explorerTreeFilter = new ExplorerTreeFilter(Set.of("t1", "t2"), Set.of("t1", "t2"), Set.of("p1", "p2"), "blah", true);

        final FindExplorerNodeCriteria criteria1 = new FindExplorerNodeCriteria();
        criteria1.setOpenItems(Set.of(explorerNode.getUuid()));
        criteria1.setTemporaryOpenedItems(Set.of(explorerNode.getUuid()));
        criteria1.setFilter(explorerTreeFilter);
        criteria1.setMinDepth(2);
        criteria1.setEnsureVisible(Set.of(explorerNode.getUuid()));

        final ObjectMapper objectMapper = JsonUtil.getMapper();
        final String result1 = objectMapper.writeValueAsString(criteria1);
        System.out.println(result1);
        final FindExplorerNodeCriteria criteria2 = objectMapper.readValue(result1, FindExplorerNodeCriteria.class);
        final String result2 = objectMapper.writerFor(FindExplorerNodeCriteria.class).writeValueAsString(criteria2);
        System.out.println(result2);

        assertThat(result1).isEqualTo(result2);
        assertThat(criteria2).isEqualTo(criteria1);
    }

    @Test
    void testFetchResponse() throws Exception {
        final ExplorerNode child = new ExplorerNode("test-type", "child-uuid", "child-name", "test-tags");
        child.setNodeState(NodeState.LEAF);

        final ExplorerNode parent = new ExplorerNode("test-type", "parent-uuid", "parent-name", "test-tags");
        parent.setNodeState(NodeState.OPEN);
        parent.setChildren(List.of(child));

        final FetchExplorerNodeResult result1 = new FetchExplorerNodeResult();
        result1.getRootNodes().add(parent);
        result1.setTemporaryOpenedItems(Set.of(parent.getUuid()));
        result1.setOpenedItems(List.of(parent.getUuid()));

        final ObjectMapper objectMapper = JsonUtil.getMapper();
        final String string1 = objectMapper.writeValueAsString(result1);
        System.out.println(string1);
        final FetchExplorerNodeResult result2 = objectMapper.readValue(string1, FetchExplorerNodeResult.class);
        final String string2 = objectMapper.writerFor(FetchExplorerNodeResult.class).writeValueAsString(result2);
        System.out.println(string2);

        Response.ok().entity(result1);

        assertThat(string1).isEqualTo(string2);
        assertThat(result2).isEqualTo(result1);
    }
}
