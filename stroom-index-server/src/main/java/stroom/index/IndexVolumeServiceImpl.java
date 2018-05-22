package stroom.index;

import stroom.entity.StroomEntityManager;
import stroom.entity.util.SqlBuilder;
import stroom.node.VolumeService;
import stroom.node.shared.Volume;
import stroom.persist.EntityManagerSupport;
import stroom.docref.DocRef;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexVolumeServiceImpl implements IndexVolumeService {
    private final StroomEntityManager entityManager;
    private final EntityManagerSupport entityManagerSupport;
    private final VolumeService volumeService;

    @Inject
    IndexVolumeServiceImpl(final StroomEntityManager entityManager,
                           final EntityManagerSupport entityManagerSupport,
                           final VolumeService volumeService) {
        this.entityManager = entityManager;
        this.entityManagerSupport = entityManagerSupport;
        this.volumeService = volumeService;
    }

    @SuppressWarnings("unchecked")
    public Set<Volume> getVolumesForIndex(final DocRef indexRef) {
        final SqlBuilder builder = new SqlBuilder();
        builder.append("SELECT FK_VOL_ID FROM IDX_VOL WHERE IDX_UUID = ");
        builder.arg(indexRef.getUuid());
        final List<Integer> list = entityManager.executeNativeQueryResultList(builder);
        return list.stream()
                .map(id -> Optional.ofNullable(volumeService.loadById(id)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    public void setVolumesForIndex(final DocRef indexRef, final Set<Volume> volumes) {
        entityManagerSupport.transaction(em -> {
            final SqlBuilder builder = new SqlBuilder();
            builder.append("DELETE FROM IDX_VOL WHERE IDX_UUID = ");
            builder.arg(indexRef.getUuid());
            entityManager.executeNativeUpdate(builder);

            volumes.forEach(vol -> {
                final SqlBuilder sb = new SqlBuilder();
                sb.append("INSERT INTO IDX_VOL (IDX_UUID, FK_VOL_ID) VALUES (");
                sb.arg(indexRef.getUuid());
                sb.append(", ");
                sb.arg(vol.getId());
                sb.append(")");

                entityManager.executeNativeUpdate(sb);
            });
        });
    }
}
