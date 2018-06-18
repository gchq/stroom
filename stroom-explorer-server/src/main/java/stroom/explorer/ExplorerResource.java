package stroom.explorer;

import io.swagger.annotations.Api;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.HasNodeState;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Api(
        value = "explorer - /v1",
        description = "Stroom Explorer API")
@Path("/explorer/v1")
@Produces(MediaType.APPLICATION_JSON)
public class ExplorerResource {
    private final ExplorerNodeService explorerNodeService;
    private final ExplorerTreeModel explorerTreeModel;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;

    @Inject
    public ExplorerResource(final ExplorerNodeService explorerNodeService,
                            final ExplorerTreeModel explorerTreeModel,
                            final ExplorerActionHandlers explorerActionHandlers,
                            final SecurityContext securityContext,
                            final ExplorerEventLog explorerEventLog) {
        this.explorerNodeService = explorerNodeService;
        this.explorerTreeModel = explorerTreeModel;
        this.explorerActionHandlers = explorerActionHandlers;
        this.securityContext = securityContext;
        this.explorerEventLog = explorerEventLog;
    }

    @GET
    @Path("/all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExplorerTree() {
        // For now, just do this every time the whole tree is fetched
        explorerTreeModel.rebuild();

        final TreeModel treeModel = explorerTreeModel.getModel();
        final TreeModel filteredModel = new TreeModelImpl();

        filterDescendants(null, treeModel, filteredModel, 0, DocumentPermissionNames.READ);
        final SimpleDocRefTreeDTO result = getRoot(filteredModel);

        return Response.ok(result).build();
    }

    private boolean filterDescendants(final ExplorerNode parent,
                                   final TreeModel treeModelIn,
                                   final TreeModel treeModelOut,
                                   final int currentDepth,
                                   final String...documentPermissionNames) {
        int added = 0;

        final List<ExplorerNode> children = treeModelIn.getChildMap().get(parent);
        if (children != null) {

            for (final ExplorerNode child : children) {
                // Recurse right down to find out if a descendant is being added and therefore if we need to include this as an ancestor.
                final boolean hasChildren = filterDescendants(child, treeModelIn, treeModelOut, currentDepth + 1, documentPermissionNames);
                if (hasChildren) {
                    treeModelOut.add(parent, child);
                    added++;

                } else if (checkSecurity(child, documentPermissionNames)) {
                    treeModelOut.add(parent, child);
                    added++;
                }
            }
        }

        return (added > 0);
    }

    private boolean checkSecurity(final ExplorerNode explorerNode, final String ... requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.length == 0) {
            return false;
        }

        final String type = explorerNode.getType();
        final String uuid = explorerNode.getDocRef().getUuid();
        for (final String permission : requiredPermissions) {
            if (!securityContext.hasDocumentPermission(type, uuid, permission)) {
                return false;
            }
        }

        return true;
    }

    private SimpleDocRefTreeDTO getRoot(final TreeModel filteredModel) {
        SimpleDocRefTreeDTO result = null;

        final List<ExplorerNode> children = filteredModel.getChildMap().get(null);
        if (children != null) {
            for (final ExplorerNode child : children) {
                result = new SimpleDocRefTreeDTO(child.getUuid(), child.getType(), child.getName());
                getChildren(child, filteredModel).forEach(result::addChild);
            }
        }

        return result;
    }

    private List<SimpleDocRefTreeDTO> getChildren(final ExplorerNode parent,
                                                  final TreeModel filteredModel) {
        List<SimpleDocRefTreeDTO> result = new ArrayList<>();

        final List<ExplorerNode> children = filteredModel.getChildMap().get(parent);

        if (children == null) {
            parent.setNodeState(HasNodeState.NodeState.LEAF);
        } else {
            parent.setNodeState(HasNodeState.NodeState.OPEN);
            for (final ExplorerNode child : children) {
                final SimpleDocRefTreeDTO resultChild = new SimpleDocRefTreeDTO(child.getUuid(), child.getType(), child.getName());
                getChildren(child, filteredModel).forEach(resultChild::addChild);
                result.add(resultChild);
            }
        }

        return result;
    }
}
