package stroom.node.shared;

import stroom.util.shared.ResultPage;

import java.util.List;

public class FetchNodeStatusResponse extends ResultPage<NodeStatusResult> {
    public FetchNodeStatusResponse() {
    }

    public FetchNodeStatusResponse(final List<NodeStatusResult> list) {
        super(list);
    }
}
