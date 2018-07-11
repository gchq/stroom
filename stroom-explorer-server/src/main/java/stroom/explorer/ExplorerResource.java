package stroom.explorer;

import io.swagger.annotations.Api;
import stroom.docref.DocRef;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.shared.BulkActionResult;
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
@Consumes(MediaType.APPLICATION_JSON)
public class ExplorerResource {
    private final ExplorerService explorerService;
    private final ExplorerTreeModel explorerTreeModel;
    private final SecurityContext securityContext;

    @Inject
    public ExplorerResource(final ExplorerService explorerService,
                            final ExplorerTreeModel explorerTreeModel,
                            final SecurityContext securityContext) {
        this.explorerService = explorerService;
        this.explorerTreeModel = explorerTreeModel;
        this.securityContext = securityContext;
    }

    @GET
    @Path("/all")
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

    static class CopyOp {
        private List<DocRef> docRefs;
        private DocRef destinationFolderRef;
        private PermissionInheritance permissionInheritance;

        public List<DocRef> getDocRefs() {
            return docRefs;
        }

        public void setDocRefs(List<DocRef> docRefs) {
            this.docRefs = docRefs;
        }

        public DocRef getDestinationFolderRef() {
            return destinationFolderRef;
        }

        public void setDestinationFolderRef(DocRef destinationFolderRef) {
            this.destinationFolderRef = destinationFolderRef;
        }

        public PermissionInheritance getPermissionInheritance() {
            return permissionInheritance;
        }

        public void setPermissionInheritance(PermissionInheritance permissionInheritance) {
            this.permissionInheritance = permissionInheritance;
        }
    }

    @POST
    @Path("/copy")
    public Response copyDocument(final CopyOp op) {
        final BulkActionResult result = explorerService.copy(op.docRefs, op.destinationFolderRef, op.permissionInheritance);

        return Response.ok(result).build();
    }

    static class MoveOp {
        private List<DocRef> docRefs;
        private DocRef destinationFolderRef;
        private PermissionInheritance permissionInheritance;

        public List<DocRef> getDocRefs() {
            return docRefs;
        }

        public void setDocRefs(List<DocRef> docRefs) {
            this.docRefs = docRefs;
        }

        public DocRef getDestinationFolderRef() {
            return destinationFolderRef;
        }

        public void setDestinationFolderRef(DocRef destinationFolderRef) {
            this.destinationFolderRef = destinationFolderRef;
        }

        public PermissionInheritance getPermissionInheritance() {
            return permissionInheritance;
        }

        public void setPermissionInheritance(PermissionInheritance permissionInheritance) {
            this.permissionInheritance = permissionInheritance;
        }
    }

    @PUT
    @Path("/move")
    public Response moveDocument(final MoveOp op) {
        final BulkActionResult result = explorerService.move(op.docRefs, op.destinationFolderRef, op.permissionInheritance);

        return Response.ok(result).build();
    }

    static class RenameOp {
        private DocRef docRef;
        private String name;

        public DocRef getDocRef() {
            return docRef;
        }

        public void setDocRef(DocRef docRef) {
            this.docRef = docRef;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @PUT
    @Path("/rename")
    public Response renameDocument(final RenameOp renameOp) {
        final DocRef result = explorerService.rename(renameOp.docRef, renameOp.name);

        return Response.ok(result).build();
    }

    @DELETE
    @Path("/delete")
    public Response deleteDocument(final List<DocRef> docRefs) {
        final BulkActionResult result = explorerService.delete(docRefs);

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
