package stroom.index.rest;

import stroom.index.service.IndexVolumeService;
import stroom.index.shared.IndexVolume;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;

public class IndexVolumeResourceImpl implements IndexVolumeResource {
    private final IndexVolumeService indexVolumeService;

    @Inject
    public IndexVolumeResourceImpl(final IndexVolumeService indexVolumeService) {
        this.indexVolumeService = indexVolumeService;
    }

    @Override
    public Response getVolumesInGroup(final String groupName) {
        final List<IndexVolume> volumes = indexVolumeService.getVolumesInGroup(groupName);

        return Response.ok(volumes).build();
    }

    @Override
    public Response addVolumeToGroup(final Long volumeId,
                                     final String name) {
        indexVolumeService.addVolumeToGroup(volumeId, name);

        return Response.noContent().build();
    }

    @Override
    public Response removeVolumeFromGroup(final Long volumeId,
                                          final String name) {
        indexVolumeService.removeVolumeFromGroup(volumeId, name);

        return Response.noContent().build();
    }
}
