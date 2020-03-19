package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;

public class NewUIIndexVolumeResourceImpl implements NewUIIndexVolumeResource {
    private final IndexVolumeService indexVolumeService;

    @Inject
    public NewUIIndexVolumeResourceImpl(final IndexVolumeService indexVolumeService) {
        this.indexVolumeService = indexVolumeService;
    }

    @Override
    public Response getAll() {
        final List<IndexVolume> indexVolumes = indexVolumeService.find(new ExpressionCriteria()).getValues();
        return Response.ok(indexVolumes).build();
    }

    @Override
    public Response getById(final int id) {
        final IndexVolume indexVolume = indexVolumeService.read(id);
        return Response.ok(indexVolume).build();
    }

    @Override
    public Response create(final IndexVolume indexVolume) {
        final IndexVolume result = indexVolumeService.create(indexVolume);
        return Response.ok(result).build();
    }

    @Override
    public Response update(final IndexVolume indexVolume) {
        final IndexVolume result = indexVolumeService.update(indexVolume);
        return Response.ok(result).build();
    }

    @Override
    public Response delete(final int id) {
        indexVolumeService.delete(id);
        return Response.noContent().build();
    }

//    @Override
//    public Response getVolumesInGroup(final String groupName) {
//        final List<IndexVolume> volumes = indexVolumeService.getVolumesInGroup(groupName);
//
//        return Response.ok(volumes).build();
//    }

//    @Override
//    public Response getGroupsForVolume(final int id) {
//        final List<IndexVolumeGroup> groups = indexVolumeService.getGroupsForVolume(id);
//
//        return Response.ok(groups).build();
//    }
//
//    @Override
//    public Response addVolumeToGroup(final int volumeId,
//                                     final String groupName) {
//        indexVolumeService.addVolumeToGroup(volumeId, groupName);
//
//        return Response.noContent().build();
//    }

//    @Override
//    public Response removeVolumeFromGroup(final int volumeId,
//                                          final String groupName) {
//        indexVolumeService.removeVolumeFromGroup(volumeId, groupName);
//
//        return Response.noContent().build();
//    }
}
