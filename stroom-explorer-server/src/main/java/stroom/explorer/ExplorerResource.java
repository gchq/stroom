package stroom.explorer;

import io.swagger.annotations.Api;
import stroom.docref.DocRef;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.HasNodeState;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Api(
        value = "explorer - /v1",
        description = "Stroom Explorer API")
@Path("/explorer/v1")
@Produces(MediaType.APPLICATION_JSON)
public class ExplorerResource {
    private final ExplorerService explorerService;
    private final ExplorerNodeService explorerNodeService;
    private final ExplorerTreeModel explorerTreeModel;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;

    @Inject
    public ExplorerResource(final ExplorerService explorerService,
                            final ExplorerNodeService explorerNodeService,
                            final ExplorerTreeModel explorerTreeModel,
                            final ExplorerActionHandlers explorerActionHandlers,
                            final SecurityContext securityContext,
                            final ExplorerEventLog explorerEventLog) {
        this.explorerService = explorerService;
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

    @GET
    @Path("/info/{type}/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocInfo(@PathParam("type") final String type,
                               @PathParam("uuid") final String uuid) {
        final DocRefInfo info = explorerService.info(new DocRef.Builder()
                .type(type)
                .uuid(uuid)
                .build());

        return Response.ok(info).build();
    }

    /**
     * @return The DocRef types currently used in this tree.
     */
    @GET
    @Path("/docRefTypes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocRefTypes() {
        explorerTreeModel.rebuild();
        final TreeModel treeModel = explorerTreeModel.getModel();

        List<String> docRefTypes = treeModel.getChildMap().values().stream()
                .flatMap(List::stream)
                .map(elementNode -> elementNode == null ? "" : elementNode.getType())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        return Response.ok(docRefTypes).build();
    }

    @POST
    @Path("/copy")
    public Response copyDocument(@FormParam("docRefs") final List<DocRef> docRefs,
                                 @FormParam("destinationFolderRef") final DocRef destinationFolderRef,
                                 @FormParam("permissionInheritance") final PermissionInheritance permissionInheritance) {
        explorerService.copy(docRefs, destinationFolderRef, permissionInheritance);

        return Response.ok().build();
    }

    /**
     * Move a set of doc refs to another folder.
     *
     * @param docRefs The doc refs to move
     *
     * @return HTTP 204 if it works.
     */
    @PUT
    @Path("/move")
    public Response moveDocument(@FormParam("docRefs") final List<DocRef> docRefs,
                                 @FormParam("destinationFolderRef") final DocRef destinationFolderRef,
                                 @FormParam("permissionInheritance") final PermissionInheritance permissionInheritance) {
        explorerService.move(docRefs, destinationFolderRef, permissionInheritance);

        return Response.ok().build();
    }

    @PUT
    @Path("/rename")
    public Response renameDocument(@FormParam("docRef") final DocRef docRef,
                                   @FormParam("name") final String name) {
        explorerService.rename(docRef, name);

        return Response.ok().build();
    }

    @DELETE
    @Path("/delete")
    public Response deleteDocument(@FormParam("docRefs") final List<DocRef> docRefs) {
        explorerService.delete(docRefs);

        return Response.ok().build();
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

    private boolean checkSecurity(final ExplorerNode explorerNode, final String... requiredPermissions) {
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
