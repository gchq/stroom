package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeRecord;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.TableField;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;

@Singleton
public class FsVolumeDaoImpl implements FsVolumeDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeService.class);

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    FsVolumeDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
    }

    @Override
    public FsVolume create(final FsVolume fileVolume) {
        return JooqUtil.contextResultWithOptimisticLocking(fsDataStoreDbConnProvider, (context) -> {
            final FsVolumeRecord record = context.newRecord(FS_VOLUME, fileVolume);
            volumeToRecord(fileVolume, record);
            record.store();
            return recordToVolume(record, fileVolume.getVolumeState());
        });
    }

    @Override
    public FsVolume update(final FsVolume fileVolume) {
        final FsVolume result = JooqUtil.contextResultWithOptimisticLocking(fsDataStoreDbConnProvider, (context) -> {
            final FsVolumeRecord record = context.newRecord(FS_VOLUME, fileVolume);
            volumeToRecord(fileVolume, record);
            // This depends on there being a field named 'id' that is what we expect it to be.
            // I'd rather this was implicit/opinionated than forced into place with an interface.
            LOGGER.debug(LambdaLogUtil.message("Updating a {} with id {}", FS_VOLUME.getName(), record.getValue("id")));
            record.update();
            return recordToVolume(record, fileVolume.getVolumeState());
        });
        result.setVolumeState(fileVolume.getVolumeState());
        return result;
    }

    @Override
    public int delete(final int id) {
        return JooqUtil.transactionResult(fsDataStoreDbConnProvider, context -> {
            final Optional<Integer> stateIdOptional = context
                    .select(FS_VOLUME.FK_FS_VOLUME_STATE_ID)
                    .from(FS_VOLUME)
                    .where(FS_VOLUME.ID.eq(id))
                    .fetchOptional(FS_VOLUME.FK_FS_VOLUME_STATE_ID);

            final int result = context
                    .deleteFrom(FS_VOLUME)
                    .where(FS_VOLUME.ID.eq(id))
                    .execute();

            stateIdOptional.ifPresent(stateId -> context
                    .deleteFrom(FS_VOLUME_STATE)
                    .where(FS_VOLUME_STATE.ID.eq(stateId))
                    .execute());

            return result;
        });
    }

    @Override
    public FsVolume fetch(final int id) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select()
                .from(FS_VOLUME)
                .join(FS_VOLUME_STATE)
                .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                .where(FS_VOLUME.ID.eq(id))
                .fetchOptional()
                .map(this::recordToVolume)
                .orElse(null));
    }

    @Override
    public ResultPage<FsVolume> find(final FindFsVolumeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                volumeStatusCriteriaSetToCondition(FS_VOLUME.STATUS, criteria.getSelection()));

        final List<FsVolume> list = JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select()
                .from(FS_VOLUME)
                .join(FS_VOLUME_STATE)
                .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                .where(conditions)
                .limit(JooqUtil.getLimit(criteria.getPageRequest(), true))
                .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                .fetch()
                .map(this::recordToVolume));
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private void volumeToRecord(final FsVolume fileVolume, final FsVolumeRecord record) {
        record.set(FS_VOLUME.STATUS, fileVolume.getStatus().getPrimitiveValue());
        record.set(FS_VOLUME.FK_FS_VOLUME_STATE_ID, fileVolume.getVolumeState().getId());
    }

    private FsVolume recordToVolume(Record record) {
        final FsVolumeState fileSystemVolumeState = new FsVolumeState(
                record.get(FS_VOLUME_STATE.ID),
                record.get(FS_VOLUME_STATE.VERSION),
                record.get(FS_VOLUME_STATE.BYTES_USED),
                record.get(FS_VOLUME_STATE.BYTES_FREE),
                record.get(FS_VOLUME_STATE.BYTES_TOTAL),
                record.get(FS_VOLUME_STATE.UPDATE_TIME_MS));
        return recordToVolume(record, fileSystemVolumeState);
    }

    private FsVolume recordToVolume(Record record, final FsVolumeState fileSystemVolumeState) {
        final FsVolume fileVolume = new FsVolume();
        fileVolume.setId(record.get(FS_VOLUME.ID));
        fileVolume.setVersion(record.get(FS_VOLUME.VERSION));
        fileVolume.setCreateTimeMs(record.get(FS_VOLUME.CREATE_TIME_MS));
        fileVolume.setCreateUser(record.get(FS_VOLUME.CREATE_USER));
        fileVolume.setUpdateTimeMs(record.get(FS_VOLUME.UPDATE_TIME_MS));
        fileVolume.setUpdateUser(record.get(FS_VOLUME.UPDATE_USER));
        fileVolume.setPath(record.get(FS_VOLUME.PATH));
        fileVolume.setStatus(VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(FS_VOLUME.STATUS)));
        fileVolume.setByteLimit(record.get(FS_VOLUME.BYTE_LIMIT));
        fileVolume.setVolumeState(fileSystemVolumeState);
        return fileVolume;
    }

    private Optional<Condition> volumeStatusCriteriaSetToCondition(final TableField<FsVolumeRecord, Byte> field, final Selection<VolumeUseStatus> selection) {
        final Selection<Byte> set = Selection.selectNone();
        set.setMatchAll(selection.isMatchAll());
        set.setSet(selection.getSet().stream().map(VolumeUseStatus::getPrimitiveValue).collect(Collectors.toSet()));
        return JooqUtil.getSetCondition(field, set);
    }
}
