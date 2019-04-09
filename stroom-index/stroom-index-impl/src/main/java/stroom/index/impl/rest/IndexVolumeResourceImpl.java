package stroom.index.impl.rest;

import stroom.index.impl.CreateVolumeDTO;
import stroom.index.impl.IndexVolumeResource;
import stroom.index.impl.IndexVolumeService;
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
    public Response getAll() {
        final List<IndexVolume> indexVolumes = indexVolumeService.getAll();
        return Response.ok(indexVolumes).build();
    }

    @Override
    public Response getById(final Long id) {
        final IndexVolume indexVolume = indexVolumeService.getById(id);
        return Response.ok(indexVolume).build();
    }

    @Override
    public Response create(final CreateVolumeDTO createVolumeDTO) {
        final IndexVolume indexVolume = indexVolumeService.create(
                createVolumeDTO.getNodeName(),
                createVolumeDTO.getPath());
        return Response.ok(indexVolume).build();
    }

    @Override
    public Response delete(final Long id) {
        indexVolumeService.delete(id);
        return Response.noContent().build();
    }

    @Override
    public Response getVolumesInGroup(final String groupName) {
        final List<IndexVolume> volumes = indexVolumeService.getVolumesInGroup(groupName);

        return Response.ok(volumes).build();
    }

    @Override
    public Response addVolumeToGroup(final Long volumeId,
                                     final String groupName) {
        indexVolumeService.addVolumeToGroup(volumeId, groupName);

        return Response.noContent().build();
    }

    @Override
    public Response removeVolumeFromGroup(final Long volumeId,
                                          final String groupName) {
        indexVolumeService.removeVolumeFromGroup(volumeId, groupName);

        return Response.noContent().build();
    }
}
