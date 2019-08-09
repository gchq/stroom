package stroom.index.impl.rest;

import stroom.index.impl.IndexVolumeGroupResource;
import stroom.index.impl.IndexVolumeGroupService;
import stroom.index.impl.UpdateIndexVolumeGroupDTO;
import stroom.index.shared.IndexVolumeGroup;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;

public class IndexVolumeGroupResourceImpl implements IndexVolumeGroupResource {

    private final IndexVolumeGroupService indexVolumeGroupService;

    @Inject
    public IndexVolumeGroupResourceImpl(final IndexVolumeGroupService indexVolumeGroupService) {
        this.indexVolumeGroupService = indexVolumeGroupService;
    }

    @Override
    public Response getNames() {
        final List<String> names = indexVolumeGroupService.getNames();

        return Response.ok(names).build();
    }

    @Override
    public Response getAll() {
        final List<IndexVolumeGroup> groups = indexVolumeGroupService.getAll();

        return Response.ok(groups).build();
    }

    @Override
    public Response get(final String name) {
        final IndexVolumeGroup group = indexVolumeGroupService.get(name);

        if (null != group) {
            return Response.ok(group).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @Override
    public Response create() {
        final IndexVolumeGroup group = indexVolumeGroupService.create();
        return Response.ok(group).build();
    }

    @Override
    public Response update(IndexVolumeGroup indexVolumeGroup){
        final var group = indexVolumeGroupService.update(indexVolumeGroup);
        return Response.ok(group).build();
    }

    @Override
    public Response delete(final String id) {
        indexVolumeGroupService.delete(Integer.parseInt(id));
        return Response.noContent().build();
    }
}
