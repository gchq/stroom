package stroom.node.client;

import com.google.gwt.core.client.GWT;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.shared.NodeResource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Consumer;

@Singleton
public class NodeCache {
    private static final NodeResource NODE_RESOURCE = GWT.create(NodeResource.class);

    private final RestFactory restFactory;

    private List<String> nodeNames;
    private List<String> enabledNodeNames;

    @Inject
    NodeCache(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void listAllNodes(final Consumer<List<String>> nodeListConsumer,
                             final Consumer<Throwable> throwableConsumer) {
        if (nodeNames == null) {
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(nodeNames -> {
                // Store node list for future queries.
                this.nodeNames = nodeNames;
                nodeListConsumer.accept(nodeNames);
            }).onFailure(throwableConsumer).call(NODE_RESOURCE).listAllNodes();
        } else {
            nodeListConsumer.accept(nodeNames);
        }
    }

    public void listEnabledNodes(final Consumer<List<String>> nodeListConsumer,
                             final Consumer<Throwable> throwableConsumer) {
        if (enabledNodeNames == null) {
            final Rest<List<String>> rest = restFactory.create();
            rest.onSuccess(nodeNames -> {
                // Store node list for future queries.
                this.enabledNodeNames = nodeNames;
                nodeListConsumer.accept(nodeNames);
            }).onFailure(throwableConsumer).call(NODE_RESOURCE).listEnabledNodes();
        } else {
            nodeListConsumer.accept(enabledNodeNames);
        }
    }
}
