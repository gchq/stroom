package stroom.analytics.impl.db;

import stroom.analytics.impl.ExecutionNode;
import stroom.analytics.impl.ExecutionScheduleDao;
import stroom.analytics.impl.db.jooq.tables.records.ExecutionScheduleRecord;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryFields;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleFields;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionTracker;
import stroom.analytics.shared.ScheduleBounds;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindUserContext;
import stroom.security.user.api.UserRefLookup;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.Condition;
import org.jooq.OrderField;
import org.jooq.Record;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static stroom.analytics.impl.db.jooq.tables.ExecutionHistory.EXECUTION_HISTORY;
import static stroom.analytics.impl.db.jooq.tables.ExecutionSchedule.EXECUTION_SCHEDULE;
import static stroom.analytics.impl.db.jooq.tables.ExecutionTracker.EXECUTION_TRACKER;

public class ExecutionScheduleDaoImpl implements ExecutionScheduleDao {

    private final AnalyticsDbConnProvider analyticsDbConnProvider;
    private final Provider<UserRefLookup> userRefLookupProvider;
    private final SecurityContext securityContext;

    @Inject
    public ExecutionScheduleDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider,
                                    final Provider<UserRefLookup> userRefLookupProvider,
                                    final SecurityContext securityContext) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
        this.userRefLookupProvider = userRefLookupProvider;
        this.securityContext = securityContext;
    }

    @Override
    public Set<ExecutionNode> fetchExecutionNodes(final DocRef analyticDocRef) {
        return Set.copyOf(JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .selectDistinct(
                                EXECUTION_SCHEDULE.NODE_NAME,
                                EXECUTION_SCHEDULE.ENABLED)
                        .from(EXECUTION_SCHEDULE)
                        .where(EXECUTION_SCHEDULE.DOC_UUID.eq(analyticDocRef.getUuid()))
                        .and(EXECUTION_SCHEDULE.DOC_TYPE.eq(analyticDocRef.getType()))
                        .and(EXECUTION_SCHEDULE.ENABLED.eq(true))
                        .fetch())
                .map(record -> new ExecutionNode(
                        record.get(EXECUTION_SCHEDULE.NODE_NAME),
                        record.get(EXECUTION_SCHEDULE.ENABLED))));
    }

    @Override
    public ResultPage<ExecutionSchedule> fetchExecutionSchedule(final ExecutionScheduleRequest request) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                Optional.ofNullable(request.getOwnerDocRef())
                        .map(DocRef::getType)
                        .map(EXECUTION_SCHEDULE.DOC_TYPE::eq),
                Optional.ofNullable(request.getOwnerDocRef())
                        .map(DocRef::getUuid)
                        .map(EXECUTION_SCHEDULE.DOC_UUID::eq),
                Optional.ofNullable(request.getNodeName())
                        .map(EXECUTION_SCHEDULE.NODE_NAME::eq),
                Optional.ofNullable(request.getEnabled())
                        .map(EXECUTION_SCHEDULE.ENABLED::eq));
        final Collection<OrderField<?>> orderFields = createExecutionScheduleOrderFields(request);
        final Integer offset = JooqUtil.getOffset(request.getPageRequest());
        final Integer limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<ExecutionSchedule> list = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .select(EXECUTION_SCHEDULE.ID,
                                EXECUTION_SCHEDULE.NAME,
                                EXECUTION_SCHEDULE.ENABLED,
                                EXECUTION_SCHEDULE.NODE_NAME,
                                EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                                EXECUTION_SCHEDULE.EXPRESSION,
                                EXECUTION_SCHEDULE.CONTIGUOUS,
                                EXECUTION_SCHEDULE.START_TIME_MS,
                                EXECUTION_SCHEDULE.END_TIME_MS,
                                EXECUTION_SCHEDULE.DOC_TYPE,
                                EXECUTION_SCHEDULE.DOC_UUID,
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID)
                        .from(EXECUTION_SCHEDULE)
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .map(this::recordToExecutionSchedule);
        return ResultPage.createCriterialBasedList(list, request);
    }

    @Override
    public Optional<ExecutionSchedule> fetchScheduleById(final int id) {
        final Collection<Condition> conditions = JooqUtil.conditions(Optional.of(EXECUTION_SCHEDULE.ID.eq(id)));

        return JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .select(EXECUTION_SCHEDULE.ID,
                                EXECUTION_SCHEDULE.NAME,
                                EXECUTION_SCHEDULE.ENABLED,
                                EXECUTION_SCHEDULE.NODE_NAME,
                                EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                                EXECUTION_SCHEDULE.EXPRESSION,
                                EXECUTION_SCHEDULE.CONTIGUOUS,
                                EXECUTION_SCHEDULE.START_TIME_MS,
                                EXECUTION_SCHEDULE.END_TIME_MS,
                                EXECUTION_SCHEDULE.DOC_TYPE,
                                EXECUTION_SCHEDULE.DOC_UUID,
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID)
                        .from(EXECUTION_SCHEDULE)
                        .where(conditions)
                        .fetchOptional())
                .map(this::recordToExecutionSchedule);
    }

    @Override
    public List<ExecutionSchedule> fetchSchedulesByRunAsUser(final String userUuid) {
        return JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .select(EXECUTION_SCHEDULE.ID,
                                EXECUTION_SCHEDULE.NAME,
                                EXECUTION_SCHEDULE.ENABLED,
                                EXECUTION_SCHEDULE.NODE_NAME,
                                EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                                EXECUTION_SCHEDULE.EXPRESSION,
                                EXECUTION_SCHEDULE.CONTIGUOUS,
                                EXECUTION_SCHEDULE.START_TIME_MS,
                                EXECUTION_SCHEDULE.END_TIME_MS,
                                EXECUTION_SCHEDULE.DOC_TYPE,
                                EXECUTION_SCHEDULE.DOC_UUID,
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID)
                        .from(EXECUTION_SCHEDULE)
                        .where(EXECUTION_SCHEDULE.RUN_AS_USER_UUID.eq(userUuid))
                        .fetch())
                .map(this::recordToExecutionSchedule);
    }

    @Override
    public ExecutionSchedule createExecutionSchedule(final ExecutionSchedule executionSchedule) {
        final UserRef runAsUser = checkRunAs(executionSchedule);

        final Optional<Integer> optionalId = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .insertInto(EXECUTION_SCHEDULE,
                                EXECUTION_SCHEDULE.NAME,
                                EXECUTION_SCHEDULE.ENABLED,
                                EXECUTION_SCHEDULE.NODE_NAME,
                                EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                                EXECUTION_SCHEDULE.EXPRESSION,
                                EXECUTION_SCHEDULE.CONTIGUOUS,
                                EXECUTION_SCHEDULE.START_TIME_MS,
                                EXECUTION_SCHEDULE.END_TIME_MS,
                                EXECUTION_SCHEDULE.DOC_TYPE,
                                EXECUTION_SCHEDULE.DOC_UUID,
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID)
                        .values(executionSchedule.getName(),
                                executionSchedule.isEnabled(),
                                executionSchedule.getNodeName(),
                                executionSchedule.getSchedule().getType().name(),
                                executionSchedule.getSchedule().getExpression(),
                                executionSchedule.isContiguous(),
                                executionSchedule.getScheduleBounds() == null
                                        ? null
                                        :
                                                executionSchedule.getScheduleBounds().getStartTimeMs(),
                                executionSchedule.getScheduleBounds() == null
                                        ? null
                                        :
                                                executionSchedule.getScheduleBounds().getEndTimeMs(),
                                executionSchedule.getOwningDoc().getType(),
                                executionSchedule.getOwningDoc().getUuid(),
                                runAsUser.getUuid())
                        .returning(EXECUTION_SCHEDULE.ID)
                        .fetchOptional())
                .map(ExecutionScheduleRecord::getId);
        return optionalId.flatMap(this::fetchScheduleById).orElse(null);
    }

    private UserRef checkRunAs(final ExecutionSchedule executionSchedule) {
        final UserRef currentUser = securityContext.getUserRef();
        if (executionSchedule.getRunAsUser() == null) {
            // By default the creator of the filter becomes the run as user for the filter
            // (see stroom.processor.impl.ProcessorTaskCreatorImpl.createNewTasks)
            return currentUser;
        } else if (!Objects.equals(executionSchedule.getRunAsUser(), currentUser) &&
                   !securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to set the run as user to '" +
                    executionSchedule.getRunAsUser().toDisplayString() +
                    "'. You can only run a filter as " +
                    "yourself unless you have manage users permission");
        }
        return executionSchedule.getRunAsUser();
    }

    @Override
    public ExecutionSchedule updateExecutionSchedule(final ExecutionSchedule executionSchedule) {
        final UserRef runAsUser = checkRunAs(executionSchedule);

        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(EXECUTION_SCHEDULE)
                .set(EXECUTION_SCHEDULE.NAME, executionSchedule.getName())
                .set(EXECUTION_SCHEDULE.ENABLED, executionSchedule.isEnabled())
                .set(EXECUTION_SCHEDULE.NODE_NAME, executionSchedule.getNodeName())
                .set(EXECUTION_SCHEDULE.SCHEDULE_TYPE, executionSchedule.getSchedule().getType().name())
                .set(EXECUTION_SCHEDULE.EXPRESSION, executionSchedule.getSchedule().getExpression())
                .set(EXECUTION_SCHEDULE.CONTIGUOUS, executionSchedule.isContiguous())
                .set(EXECUTION_SCHEDULE.START_TIME_MS,
                        executionSchedule.getScheduleBounds() == null
                                ? null
                                :
                                        executionSchedule.getScheduleBounds().getStartTimeMs())
                .set(EXECUTION_SCHEDULE.END_TIME_MS,
                        executionSchedule.getScheduleBounds() == null
                                ? null
                                :
                                        executionSchedule.getScheduleBounds().getEndTimeMs())
                .set(EXECUTION_SCHEDULE.DOC_TYPE, executionSchedule.getOwningDoc().getType())
                .set(EXECUTION_SCHEDULE.DOC_UUID, executionSchedule.getOwningDoc().getUuid())
                .set(EXECUTION_SCHEDULE.RUN_AS_USER_UUID, runAsUser.getUuid())
                .where(EXECUTION_SCHEDULE.ID.eq(executionSchedule.getId()))
                .execute());
        return fetchScheduleById(executionSchedule.getId()).orElse(null);
    }

    @Override
    public Boolean deleteExecutionSchedule(final ExecutionSchedule executionSchedule) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(EXECUTION_HISTORY)
                .where(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID.eq(executionSchedule.getId()))
                .execute());

        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(EXECUTION_TRACKER)
                .where(EXECUTION_TRACKER.FK_EXECUTION_SCHEDULE_ID.eq(executionSchedule.getId()))
                .execute());

        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(EXECUTION_SCHEDULE)
                .where(EXECUTION_SCHEDULE.ID.eq(executionSchedule.getId()))
                .execute());

        return true;
    }

    @Override
    public Optional<ExecutionTracker> getTracker(final ExecutionSchedule schedule) {
        return JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .select(EXECUTION_TRACKER.ACTUAL_EXECUTION_TIME_MS,
                                EXECUTION_TRACKER.LAST_EFFECTIVE_EXECUTION_TIME_MS,
                                EXECUTION_TRACKER.NEXT_EFFECTIVE_EXECUTION_TIME_MS)
                        .from(EXECUTION_TRACKER)
                        .where(EXECUTION_TRACKER.FK_EXECUTION_SCHEDULE_ID.eq(schedule.getId()))
                        .fetchOptional())
                .map(record -> new ExecutionTracker(
                        record.get(EXECUTION_TRACKER.ACTUAL_EXECUTION_TIME_MS),
                        record.get(EXECUTION_TRACKER.LAST_EFFECTIVE_EXECUTION_TIME_MS),
                        record.get(EXECUTION_TRACKER.NEXT_EFFECTIVE_EXECUTION_TIME_MS)));
    }

    @Override
    public void createTracker(final ExecutionSchedule executionSchedule, final ExecutionTracker executionTracker) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(EXECUTION_TRACKER)
                .columns(
                        EXECUTION_TRACKER.FK_EXECUTION_SCHEDULE_ID,
                        EXECUTION_TRACKER.ACTUAL_EXECUTION_TIME_MS,
                        EXECUTION_TRACKER.LAST_EFFECTIVE_EXECUTION_TIME_MS,
                        EXECUTION_TRACKER.NEXT_EFFECTIVE_EXECUTION_TIME_MS)
                .values(
                        executionSchedule.getId(),
                        executionTracker.getActualExecutionTimeMs(),
                        executionTracker.getLastEffectiveExecutionTimeMs(),
                        executionTracker.getNextEffectiveExecutionTimeMs())
                .execute());
    }

    @Override
    public void updateTracker(final ExecutionSchedule executionSchedule, final ExecutionTracker executionTracker) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(EXECUTION_TRACKER)
                .set(EXECUTION_TRACKER.ACTUAL_EXECUTION_TIME_MS, executionTracker.getActualExecutionTimeMs())
                .set(EXECUTION_TRACKER.LAST_EFFECTIVE_EXECUTION_TIME_MS,
                        executionTracker.getLastEffectiveExecutionTimeMs())
                .set(EXECUTION_TRACKER.NEXT_EFFECTIVE_EXECUTION_TIME_MS,
                        executionTracker.getNextEffectiveExecutionTimeMs())
                .where(EXECUTION_TRACKER.FK_EXECUTION_SCHEDULE_ID.eq(executionSchedule.getId()))
                .execute());
    }

    @Override
    public ResultPage<ExecutionHistory> fetchExecutionHistory(final ExecutionHistoryRequest request) {
        // Only filter on the user in the DB as we don't have a jooq/sql version of the
        // QuickFilterPredicateFactory
        final Collection<Condition> conditions = JooqUtil.conditions(
                Optional.ofNullable(request.getExecutionSchedule().getId())
                        .map(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID::eq));
        final Collection<OrderField<?>> orderFields = createExecutionHistoryOrderFields(request);
        final Integer offset = JooqUtil.getOffset(request.getPageRequest());
        final Integer limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<ExecutionHistory> list = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .select(EXECUTION_HISTORY.ID,
                                EXECUTION_HISTORY.EXECUTION_TIME_MS,
                                EXECUTION_HISTORY.EFFECTIVE_EXECUTION_TIME_MS,
                                EXECUTION_HISTORY.STATUS,
                                EXECUTION_HISTORY.MESSAGE,
                                EXECUTION_SCHEDULE.ID,
                                EXECUTION_SCHEDULE.NAME,
                                EXECUTION_SCHEDULE.ENABLED,
                                EXECUTION_SCHEDULE.NODE_NAME,
                                EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                                EXECUTION_SCHEDULE.EXPRESSION,
                                EXECUTION_SCHEDULE.CONTIGUOUS,
                                EXECUTION_SCHEDULE.START_TIME_MS,
                                EXECUTION_SCHEDULE.END_TIME_MS,
                                EXECUTION_SCHEDULE.DOC_TYPE,
                                EXECUTION_SCHEDULE.DOC_UUID,
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID)
                        .from(EXECUTION_HISTORY)
                        .join(EXECUTION_SCHEDULE)
                        .on(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID.eq(EXECUTION_SCHEDULE.ID))
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .map(this::recordToExecutionHistory);
        return ResultPage.createCriterialBasedList(list, request);
    }

    @Override
    public void addExecutionHistory(final ExecutionHistory executionHistory) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(EXECUTION_HISTORY)
                .columns(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID,
                        EXECUTION_HISTORY.EXECUTION_TIME_MS,
                        EXECUTION_HISTORY.EFFECTIVE_EXECUTION_TIME_MS,
                        EXECUTION_HISTORY.STATUS,
                        EXECUTION_HISTORY.MESSAGE)
                .values(executionHistory.getExecutionSchedule().getId(),
                        executionHistory.getExecutionTimeMs(),
                        executionHistory.getEffectiveExecutionTimeMs(),
                        executionHistory.getStatus(),
                        executionHistory.getMessage())
                .execute());
    }

    @Override
    public ExecutionTracker fetchTracker(final ExecutionSchedule schedule) {
        return getTracker(schedule).orElse(null);
    }

    private ExecutionSchedule recordToExecutionSchedule(final Record record) {
        final ScheduleType scheduleType = ScheduleType.valueOf(record.get(EXECUTION_SCHEDULE.SCHEDULE_TYPE));
        final Schedule schedule = new Schedule(scheduleType, record.get(EXECUTION_SCHEDULE.EXPRESSION));
        final ScheduleBounds scheduleBounds = new ScheduleBounds(
                record.get(EXECUTION_SCHEDULE.START_TIME_MS),
                record.get(EXECUTION_SCHEDULE.END_TIME_MS));
        final DocRef docRef = new DocRef(
                record.get(EXECUTION_SCHEDULE.DOC_TYPE),
                record.get(EXECUTION_SCHEDULE.DOC_UUID));
        return ExecutionSchedule
                .builder()
                .id(record.get(EXECUTION_SCHEDULE.ID))
                .name(record.get(EXECUTION_SCHEDULE.NAME))
                .enabled(record.get(EXECUTION_SCHEDULE.ENABLED))
                .nodeName(record.get(EXECUTION_SCHEDULE.NODE_NAME))
                .schedule(schedule)
                .contiguous(record.get(EXECUTION_SCHEDULE.CONTIGUOUS))
                .scheduleBounds(scheduleBounds)
                .owningDoc(docRef)
                .runAsUser(userRefLookupProvider
                        .get()
                        .getByUuid(record.get(EXECUTION_SCHEDULE.RUN_AS_USER_UUID), FindUserContext.RUN_AS)
                        .orElse(null))
                .build();
    }

    private ExecutionHistory recordToExecutionHistory(final Record record) {
        final ExecutionSchedule executionSchedule = recordToExecutionSchedule(record);
        return ExecutionHistory
                .builder()
                .id(record.get(EXECUTION_HISTORY.ID))
                .executionSchedule(executionSchedule)
                .executionTimeMs(record.get(EXECUTION_HISTORY.EXECUTION_TIME_MS))
                .effectiveExecutionTimeMs(record.get(EXECUTION_HISTORY.EFFECTIVE_EXECUTION_TIME_MS))
                .status(record.get(EXECUTION_HISTORY.STATUS))
                .message(record.get(EXECUTION_HISTORY.MESSAGE))
                .build();
    }

    private Collection<OrderField<?>> createExecutionScheduleOrderFields(final ExecutionScheduleRequest request) {
        if (request.getSortList() == null || request.getSortList().isEmpty()) {
            return Collections.singleton(EXECUTION_SCHEDULE.ID);
        }

        final List<OrderField<?>> list = new ArrayList<>();
        request.getSortList().forEach(sort -> {
            if (ExecutionScheduleFields.ID.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.ID.desc()
                        : EXECUTION_SCHEDULE.ID);
            } else if (ExecutionScheduleFields.NAME.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.NAME.desc()
                        : EXECUTION_SCHEDULE.NAME);
            } else if (ExecutionScheduleFields.ENABLED.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.ENABLED.desc()
                        : EXECUTION_SCHEDULE.ENABLED);
            } else if (ExecutionScheduleFields.NODE_NAME.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.NODE_NAME.desc()
                        : EXECUTION_SCHEDULE.NODE_NAME);
            } else if (ExecutionScheduleFields.SCHEDULE.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.SCHEDULE_TYPE.desc()
                        : EXECUTION_SCHEDULE.SCHEDULE_TYPE);
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.EXPRESSION.desc()
                        : EXECUTION_SCHEDULE.EXPRESSION);
            } else if (ExecutionScheduleFields.BOUNDS.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.START_TIME_MS.desc()
                        : EXECUTION_SCHEDULE.START_TIME_MS);
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.END_TIME_MS.desc()
                        : EXECUTION_SCHEDULE.END_TIME_MS);
            } else {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.ID.desc()
                        : EXECUTION_SCHEDULE.ID);
            }
        });
        return list;
    }

    private Collection<OrderField<?>> createExecutionHistoryOrderFields(final ExecutionHistoryRequest request) {
        if (request.getSortList() == null || request.getSortList().isEmpty()) {
            return Collections.singleton(EXECUTION_HISTORY.ID.desc());
        }

        final List<OrderField<?>> list = new ArrayList<>();
        request.getSortList().forEach(sort -> {
            if (ExecutionHistoryFields.ID.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_HISTORY.ID.desc()
                        : EXECUTION_HISTORY.ID);
            } else if (ExecutionHistoryFields.EXECUTION_TIME.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_HISTORY.EXECUTION_TIME_MS.desc()
                        : EXECUTION_HISTORY.EXECUTION_TIME_MS);
            } else if (ExecutionHistoryFields.EFFECTIVE_EXECUTION_TIME.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_HISTORY.EFFECTIVE_EXECUTION_TIME_MS.desc()
                        : EXECUTION_HISTORY.EFFECTIVE_EXECUTION_TIME_MS);
            } else if (ExecutionHistoryFields.STATUS.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_HISTORY.STATUS.desc()
                        : EXECUTION_HISTORY.STATUS);
            } else if (ExecutionHistoryFields.MESSAGE.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_HISTORY.MESSAGE.desc()
                        : EXECUTION_HISTORY.MESSAGE);
            } else {
                list.add(sort.isDesc()
                        ? EXECUTION_HISTORY.ID.desc()
                        : EXECUTION_HISTORY.ID);
            }
        });
        return list;
    }

    @Override
    public void deleteOldExecutionHistory(final Instant age) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(EXECUTION_HISTORY)
                .where(EXECUTION_HISTORY.EXECUTION_TIME_MS.lt(age.toEpochMilli()))
                .execute());
    }
}
