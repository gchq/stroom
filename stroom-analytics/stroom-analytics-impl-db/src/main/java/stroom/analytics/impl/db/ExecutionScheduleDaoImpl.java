/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.analytics.impl.db;

import stroom.analytics.impl.ExecutionNode;
import stroom.analytics.impl.ExecutionScheduleDao;
import stroom.analytics.impl.db.jooq.tables.records.ExecutionScheduleRecord;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryFields;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleFields;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionTracker;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ScheduleBounds;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindUserContext;
import stroom.security.shared.UserFields;
import stroom.security.user.api.UserRefLookup;
import stroom.util.scheduler.CronTrigger;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ModelStringUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
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
import java.util.UUID;

import static stroom.analytics.impl.db.jooq.tables.ExecutionHistory.EXECUTION_HISTORY;
import static stroom.analytics.impl.db.jooq.tables.ExecutionSchedule.EXECUTION_SCHEDULE;
import static stroom.analytics.impl.db.jooq.tables.ExecutionTracker.EXECUTION_TRACKER;

public class ExecutionScheduleDaoImpl implements ExecutionScheduleDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExecutionScheduleDaoImpl.class);

    private final AnalyticsDbConnProvider analyticsDbConnProvider;
    private final Provider<UserRefLookup> userRefLookupProvider;
    private final SecurityContext securityContext;
    private final DocRefInfoService docRefInfoService;
    private final ExpressionMapper expressionMapper;

    @Inject
    public ExecutionScheduleDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider,
                                    final Provider<UserRefLookup> userRefLookupProvider,
                                    final SecurityContext securityContext,
                                    final DocRefInfoService docRefInfoService,
                                    final ExpressionMapperFactory expressionMapperFactory) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
        this.userRefLookupProvider = userRefLookupProvider;
        this.securityContext = securityContext;
        this.docRefInfoService = docRefInfoService;

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ExecutionScheduleFields.FIELD_ID, EXECUTION_SCHEDULE.ID, Integer::valueOf);
        expressionMapper.map(ExecutionScheduleFields.FIELD_NAME, EXECUTION_SCHEDULE.NAME, String::valueOf);
        expressionMapper.map(ExecutionScheduleFields.FIELD_ENABLED, EXECUTION_SCHEDULE.ENABLED, Boolean::valueOf);
        expressionMapper.map(ExecutionScheduleFields.FIELD_NODE_NAME, EXECUTION_SCHEDULE.NODE_NAME, String::valueOf);
        expressionMapper.map(ExecutionScheduleFields.FIELD_SCHEDULE, EXECUTION_SCHEDULE.EXPRESSION, String::valueOf);
        expressionMapper.map(
                ExecutionScheduleFields.FIELD_SCHEDULE_TYPE,
                EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                String::valueOf
        );
        expressionMapper.map(
                ExecutionScheduleFields.FIELD_RUN_AS_USER,
                EXECUTION_SCHEDULE.RUN_AS_USER_UUID,
                String::valueOf
        );
        expressionMapper.map(ExecutionScheduleFields.FIELD_START_TIME, EXECUTION_SCHEDULE.START_TIME_MS, Long::valueOf);
        expressionMapper.map(ExecutionScheduleFields.FIELD_END_TIME, EXECUTION_SCHEDULE.END_TIME_MS, Long::valueOf);
        // Get 0-many uuids for a pipe name (partial/wild-carded)
        expressionMapper.multiMap(
                ExecutionScheduleFields.FIELD_PARENT_RULE,
                EXECUTION_SCHEDULE.DOC_UUID,
                this::getRuleUuidsByName,
                true
        );
        expressionMapper.multiMap(
                ExecutionScheduleFields.FIELD_PARENT_REPORT,
                EXECUTION_SCHEDULE.DOC_UUID,
                this::getReportUuidsByName,
                true
        );
        expressionMapper.map(
                ExecutionScheduleFields.FIELD_PARENT_DOC_TYPE,
                EXECUTION_SCHEDULE.DOC_TYPE,
                String::valueOf
        );
    }

    private List<String> getRuleUuidsByName(final List<String> names) {
        //May be able to join with reports but would likely require a breaking change to QueryField.

        // Can't cache this in a simple map due to pipes being renamed, but
        // docRefInfoService should cache most of this anyway.
        return docRefInfoService
                .findByNames(AnalyticRuleDoc.TYPE, names, true)
                .stream()
                .map(DocRef::getUuid)
                .toList();
    }

    private List<String> getReportUuidsByName(final List<String> names) {
        //May be able to join with Rules but would likely require a breaking change to QueryField.

        // Can't cache this in a simple map due to pipes being renamed, but
        // docRefInfoService should cache most of this anyway.
        return docRefInfoService
                .findByNames(ReportDoc.TYPE, names, true)
                .stream()
                .map(DocRef::getUuid)
                .toList();
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
        //Move to a software sort if non-table columns are part of the sort
        if (request.getSortList() != null && !request.getSortList().isEmpty()) {
            for (final CriteriaFieldSort sort : request.getSortList()) {
                if (ExecutionScheduleFields.PARENT_DOC.equals(sort.getId())
                    || ExecutionScheduleFields.SCHEDULE.equals(sort.getId())
                    || ExecutionScheduleFields.RUN_AS_USER.equals(sort.getId())
                    || UserFields.DISPLAY_NAME.getFldName().equals(sort.getId())
                    || ExecutionScheduleFields.PARENT_DOC_TYPE.equals(sort.getId())) {
                    return getSoftwareSortedSchedules(request);
                }
            }
        }
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
                        .map(EXECUTION_SCHEDULE.ENABLED::eq),
                Optional.ofNullable(expressionMapper.apply(request.getExpression()))
        );
        final Integer offset = JooqUtil.getOffset(request.getPageRequest());
        final Integer limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final Collection<OrderField<?>> orderFields = createExecutionScheduleOrderFields(request);

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
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID,
                                EXECUTION_SCHEDULE.UUID)
                        .from(EXECUTION_SCHEDULE)
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .map(this::recordToExecutionSchedule);
        return ResultPage.createCriterialBasedList(list, request);
    }

    //Handles retrieval of rules when you can't just sort based on database columns.
    //Currently includes RULE_NAME, RUN_AS_USER, and SCHEDULE.
    //TODO 8192 Is support for multiple sorts necessary?
    // Currently does not support the other sort types so cannot combine all sorts.
    private ResultPage<ExecutionSchedule> getSoftwareSortedSchedules(final ExecutionScheduleRequest request) {
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
                        .map(EXECUTION_SCHEDULE.ENABLED::eq),
                Optional.ofNullable(expressionMapper.apply(request.getExpression()))
        );
        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

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
                        .fetch())
                .map(this::recordToExecutionSchedule);

        request.getSortList().forEach(sort -> {
            if (ExecutionScheduleFields.PARENT_DOC.equals(sort.getId())) {
                list.sort((a, b) -> {
                    final String aName = docRefInfoService.name(a.getOwningDoc()).orElse("");
                    final String bName = docRefInfoService.name(b.getOwningDoc()).orElse("");
                    return sort.isDesc() ? -aName.compareToIgnoreCase(bName) : aName.compareToIgnoreCase(bName);
                });
            }
            if (ExecutionScheduleFields.PARENT_DOC_TYPE.equals(sort.getId())) {
                list.sort((a, b) -> {
                    final Optional<DocRefInfo> aInfo = docRefInfoService.info(a.getOwningDoc());
                    final Optional<DocRefInfo> bInfo = docRefInfoService.info(b.getOwningDoc());

                    final String aType = aInfo.map(docRefInfo -> docRefInfo.getDocRef().getType()).orElse("");
                    final String bType = bInfo.map(docRefInfo -> docRefInfo.getDocRef().getType()).orElse("");

                    return sort.isDesc() ? -aType.compareToIgnoreCase(bType) : aType.compareToIgnoreCase(bType);
                });
            }
            if (ExecutionScheduleFields.SCHEDULE.equals(sort.getId())) {
                list.sort((a, b) -> {
                    final Schedule aSchedule = a.getSchedule();
                    final Schedule bSchedule = b.getSchedule();
                    //Split the two types
                    if (!aSchedule.getType().equals(bSchedule.getType())) {
                        return sort.isDesc()
                                ? -aSchedule.getType().compareTo(bSchedule.getType())
                                : aSchedule.getType().compareTo(bSchedule.getType());
                    }
                    //Sort by simple frequency
                    if (aSchedule.getType() == ScheduleType.FREQUENCY) {
                        final Long aFreq = ModelStringUtil.parseDurationString(aSchedule.getExpression());
                        final Long bFreq = ModelStringUtil.parseDurationString(bSchedule.getExpression());
                        return sort.isDesc() ? -aFreq.compareTo(bFreq) : aFreq.compareTo(bFreq);
                    } else if (aSchedule.getType() == ScheduleType.CRON) {
                        //Sort by frequency then by time offset from zero.
                        //Treats <0 0 0 0 0> as the earliest, and <59 23 31 12 7> as the latest.
                        final CronTrigger aTrigger = new CronTrigger(aSchedule.getExpression());
                        final CronTrigger bTrigger = new CronTrigger(bSchedule.getExpression());

                        //Use -1 instead of zero so that schedules like <0 0 * * *>
                        //get correctly sorted to before <0 1 * * *>.
                        final Instant baseEpoch = Instant.ofEpochMilli(-1);

                        final Instant aInst1 = aTrigger.getNextExecutionTimeAfter(baseEpoch);
                        final Instant aInst2 = aTrigger.getNextExecutionTimeAfter(aInst1);

                        final Instant bInst1 = bTrigger.getNextExecutionTimeAfter(baseEpoch);
                        final Instant bInst2 = bTrigger.getNextExecutionTimeAfter(bInst1);

                        final int durationComparison = aInst1.until(aInst2).compareTo(bInst1.until(bInst2));
                        //No duration difference, so sort by offset
                        if (durationComparison == 0) {
                            final int offsetComparison = aInst1.compareTo(bInst1);
                            return sort.isDesc() ? -offsetComparison : offsetComparison;
                        }
                        return sort.isDesc() ? -durationComparison : durationComparison;
                    }
                    return 0;
                });
            }
            if (UserFields.DISPLAY_NAME.getFldName().equals(sort.getId())
                || ExecutionScheduleFields.RUN_AS_USER.equals(sort.getId())) {
                list.sort((a, b) -> {
                    final String aName = a.getRunAsUser().getDisplayName();
                    final String bName = b.getRunAsUser().getDisplayName();
                    return sort.isDesc() ? -aName.compareToIgnoreCase(bName) : aName.compareToIgnoreCase(bName);
                });
            }
        });


        return ResultPage.createCriterialBasedList(list.stream().skip(offset).limit(limit).toList(), request);
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
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID,
                                EXECUTION_SCHEDULE.UUID)
                        .from(EXECUTION_SCHEDULE)
                        .where(conditions)
                        .fetchOptional())
                .map(this::recordToExecutionSchedule);
    }

    @Override
    public Optional<ExecutionSchedule> fetchScheduleByUuid(final String uuid) {
        final Collection<Condition> conditions = JooqUtil
                .conditions(Optional.of(EXECUTION_SCHEDULE.UUID.eq(uuid)));

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
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID,
                                EXECUTION_SCHEDULE.UUID)
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
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID,
                                EXECUTION_SCHEDULE.UUID)
                        .from(EXECUTION_SCHEDULE)
                        .where(EXECUTION_SCHEDULE.RUN_AS_USER_UUID.eq(userUuid))
                        .fetch())
                .map(this::recordToExecutionSchedule);
    }

    @Override
    public ExecutionSchedule createExecutionSchedule(final ExecutionSchedule executionSchedule) {
        final UserRef runAsUser = checkRunAs(executionSchedule);

        final Optional<String> optionalUuid = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
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
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID,
                                EXECUTION_SCHEDULE.UUID)
                        .values(executionSchedule.getName(),
                                executionSchedule.isEnabled(),
                                executionSchedule.getNodeName(),
                                executionSchedule.getSchedule().getType().name(),
                                executionSchedule.getSchedule().getExpression(),
                                executionSchedule.isContiguous(),
                                executionSchedule.getScheduleBounds() == null
                                        ? null
                                        : executionSchedule.getScheduleBounds().getStartTimeMs(),
                                executionSchedule.getScheduleBounds() == null
                                        ? null
                                        : executionSchedule.getScheduleBounds().getEndTimeMs(),
                                executionSchedule.getOwningDoc().getType(),
                                executionSchedule.getOwningDoc().getUuid(),
                                runAsUser.getUuid(),
                                executionSchedule.getUuid() == null
                                        ? UUID.randomUUID().toString()
                                        : executionSchedule.getUuid())
                        .returning(EXECUTION_SCHEDULE.UUID)
                        .fetchOptional())
                .map(ExecutionScheduleRecord::getUuid);
        return optionalUuid.flatMap(this::fetchScheduleByUuid).orElse(null);
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

        final String uuid = executionSchedule.getUuid() == null
                ? UUID.randomUUID().toString()
                : executionSchedule.getUuid();

        if (executionSchedule.getUuid() == null) {
            LOGGER.info("Updating execution schedule {} with uuid {}", executionSchedule, uuid);
            JooqUtil.context(analyticsDbConnProvider, context -> context
                    .update(EXECUTION_SCHEDULE)
                    .set(EXECUTION_SCHEDULE.NAME, executionSchedule.getName())
                    .set(EXECUTION_SCHEDULE.ENABLED, executionSchedule.isEnabled())
                    .set(EXECUTION_SCHEDULE.NODE_NAME, executionSchedule.getNodeName())
                    .set(EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                            executionSchedule.getSchedule().getType().name())
                    .set(EXECUTION_SCHEDULE.EXPRESSION,
                            executionSchedule.getSchedule().getExpression())
                    .set(EXECUTION_SCHEDULE.CONTIGUOUS, executionSchedule.isContiguous())
                    .set(EXECUTION_SCHEDULE.START_TIME_MS,
                            executionSchedule.getScheduleBounds() == null
                                    ? null
                                    : executionSchedule.getScheduleBounds()
                                            .getStartTimeMs())
                    .set(EXECUTION_SCHEDULE.END_TIME_MS,
                            executionSchedule.getScheduleBounds() == null
                                    ? null
                                    : executionSchedule.getScheduleBounds()
                                            .getEndTimeMs())
                    .set(EXECUTION_SCHEDULE.DOC_TYPE, executionSchedule.getOwningDoc().getType())
                    .set(EXECUTION_SCHEDULE.DOC_UUID, executionSchedule.getOwningDoc().getUuid())
                    .set(EXECUTION_SCHEDULE.RUN_AS_USER_UUID, runAsUser.getUuid())
                    .set(EXECUTION_SCHEDULE.UUID, uuid)
                    .where(EXECUTION_SCHEDULE.ID.eq(executionSchedule.getId()))
                    .execute());
        } else {
            JooqUtil.context(analyticsDbConnProvider, context -> context
                    .update(EXECUTION_SCHEDULE)
                    .set(EXECUTION_SCHEDULE.NAME, executionSchedule.getName())
                    .set(EXECUTION_SCHEDULE.ENABLED, executionSchedule.isEnabled())
                    .set(EXECUTION_SCHEDULE.NODE_NAME, executionSchedule.getNodeName())
                    .set(EXECUTION_SCHEDULE.SCHEDULE_TYPE,
                            executionSchedule.getSchedule().getType().name())
                    .set(EXECUTION_SCHEDULE.EXPRESSION,
                            executionSchedule.getSchedule().getExpression())
                    .set(EXECUTION_SCHEDULE.CONTIGUOUS, executionSchedule.isContiguous())
                    .set(EXECUTION_SCHEDULE.START_TIME_MS,
                            executionSchedule.getScheduleBounds() == null
                                    ? null
                                    : executionSchedule.getScheduleBounds()
                                            .getStartTimeMs())
                    .set(EXECUTION_SCHEDULE.END_TIME_MS,
                            executionSchedule.getScheduleBounds() == null
                                    ? null
                                    : executionSchedule.getScheduleBounds()
                                            .getEndTimeMs())
                    .set(EXECUTION_SCHEDULE.DOC_TYPE, executionSchedule.getOwningDoc().getType())
                    .set(EXECUTION_SCHEDULE.DOC_UUID, executionSchedule.getOwningDoc().getUuid())
                    .set(EXECUTION_SCHEDULE.RUN_AS_USER_UUID, runAsUser.getUuid())
                    .where(EXECUTION_SCHEDULE.UUID.eq(executionSchedule.getUuid()))
                    .execute());
        }
        return fetchScheduleByUuid(uuid).orElse(null);
    }

    @Override
    public Boolean deleteExecutionSchedule(final ExecutionSchedule executionSchedule) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(EXECUTION_HISTORY)
                .where(executionSchedule.getUuid() == null
                        ? EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID.eq(executionSchedule.getId())
                        : EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID.eq(executionSchedule.getUuid())
                                .or(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID.isNull()
                                        .and(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID.eq(executionSchedule.getId()))))
                .execute());

        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(EXECUTION_TRACKER)
                .where(EXECUTION_TRACKER.FK_EXECUTION_SCHEDULE_ID.eq(executionSchedule.getId()))
                .execute());

        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(EXECUTION_SCHEDULE)
                .where(executionSchedule.getUuid() == null
                        ? EXECUTION_SCHEDULE.ID.eq(executionSchedule.getId())
                        : EXECUTION_SCHEDULE.UUID.eq(executionSchedule.getUuid()))
                .execute());


        return true;
    }

    @Override
    public Boolean deleteExecutionSchedules(final List<ExecutionSchedule> executionSchedules) {
        for (final ExecutionSchedule executionSchedule : executionSchedules) {
            final Boolean result = deleteExecutionSchedule(executionSchedule);
            if (!result) {
                return false;
            }
        }
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
                .set(EXECUTION_TRACKER.ACTUAL_EXECUTION_TIME_MS,
                        executionTracker.getActualExecutionTimeMs())
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
        final Collection<Condition> conditions;
        if (request.getExecutionSchedule().getUuid() != null) {
            //Logic: FK_UUID == sched_uuid OR (FK_UUID == null AND FK_ID == sched_id)
            conditions = JooqUtil.conditions(
                Optional.of(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID.eq(request.getExecutionSchedule().getUuid())
                    .or(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID.isNull()
                        .and(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID.eq(request.getExecutionSchedule().getId()))
                    )
                )
            );
        } else {
            conditions = JooqUtil.conditions(
                    Optional.ofNullable(request.getExecutionSchedule().getId())
                            .map(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_ID::eq));
        }
        final Collection<OrderField<?>> orderFields = createExecutionHistoryOrderFields(request);
        final Integer offset = JooqUtil.getOffset(request.getPageRequest());
        final Integer limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<ExecutionHistory> list = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                        .select(EXECUTION_HISTORY.ID,
                                EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID,
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
                                EXECUTION_SCHEDULE.RUN_AS_USER_UUID,
                                EXECUTION_SCHEDULE.UUID)
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
                        EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID,
                        EXECUTION_HISTORY.EXECUTION_TIME_MS,
                        EXECUTION_HISTORY.EFFECTIVE_EXECUTION_TIME_MS,
                        EXECUTION_HISTORY.STATUS,
                        EXECUTION_HISTORY.MESSAGE)
                .values(executionHistory.getExecutionSchedule().getId(),
                        executionHistory.getExecutionSchedule().getUuid(),
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
        String uuid = record.get(EXECUTION_SCHEDULE.UUID);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            final String finaluuid = uuid;
            JooqUtil.context(analyticsDbConnProvider, context -> context
                    .update(EXECUTION_SCHEDULE)
                    .set(EXECUTION_SCHEDULE.UUID, finaluuid)
                    .where(EXECUTION_SCHEDULE.ID.eq(record.get(EXECUTION_SCHEDULE.ID)))
                    .execute());
            LOGGER.debug("Assigned new UUID {} to ExecutionSchedule {}", uuid,
                    record.get(EXECUTION_SCHEDULE.ID));
        }

        final ScheduleType scheduleType = ScheduleType.valueOf(record.get(EXECUTION_SCHEDULE.SCHEDULE_TYPE));
        final Schedule schedule = new Schedule(scheduleType, record.get(EXECUTION_SCHEDULE.EXPRESSION));
        final ScheduleBounds scheduleBounds = new ScheduleBounds(
                record.get(EXECUTION_SCHEDULE.START_TIME_MS),
                record.get(EXECUTION_SCHEDULE.END_TIME_MS));
        final DocRef docRef = new DocRef(
                record.get(EXECUTION_SCHEDULE.DOC_TYPE),
                record.get(EXECUTION_SCHEDULE.DOC_UUID));
        final DocRef namedDocRef = docRef.copy().name(docRefInfoService.name(docRef).orElse("<ORPHANED>")).build();

        return ExecutionSchedule
                .builder()
                .id(record.get(EXECUTION_SCHEDULE.ID))
                .name(record.get(EXECUTION_SCHEDULE.NAME))
                .enabled(record.get(EXECUTION_SCHEDULE.ENABLED))
                .nodeName(record.get(EXECUTION_SCHEDULE.NODE_NAME))
                .schedule(schedule)
                .contiguous(record.get(EXECUTION_SCHEDULE.CONTIGUOUS))
                .scheduleBounds(scheduleBounds)
                .owningDoc(namedDocRef)
                .runAsUser(userRefLookupProvider
                        .get()
                        .getByUuid(record.get(EXECUTION_SCHEDULE.RUN_AS_USER_UUID), FindUserContext.RUN_AS)
                        .orElse(null))
                .uuid(uuid)
                .build();
    }

    private ExecutionHistory recordToExecutionHistory(final Record record) {
        final ExecutionSchedule executionSchedule = recordToExecutionSchedule(record);

        String fkExecutionScheduleUuid = record.get(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID);
        if (fkExecutionScheduleUuid == null) {
            fkExecutionScheduleUuid = executionSchedule.getUuid();
            final String finalUuid = fkExecutionScheduleUuid;
            JooqUtil.context(analyticsDbConnProvider, context -> context
                    .update(EXECUTION_HISTORY)
                    .set(EXECUTION_HISTORY.FK_EXECUTION_SCHEDULE_UUID, finalUuid)
                    .where(EXECUTION_HISTORY.ID.eq(record.get(EXECUTION_HISTORY.ID)))
                    .execute());
            LOGGER.debug("Assigned FK UUID {} to ExecutionHistory {}", finalUuid,
                    record.get(EXECUTION_HISTORY.ID));
        }

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
            if (ExecutionScheduleFields.UUID.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.UUID.desc()
                        : EXECUTION_SCHEDULE.UUID);
            } else if (ExecutionScheduleFields.PARENT_DOC.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.DOC_UUID.desc()
                        : EXECUTION_SCHEDULE.DOC_UUID);
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
            } else if (ExecutionScheduleFields.RUN_AS_USER.equals(sort.getId())) {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.RUN_AS_USER_UUID.desc()
                        : EXECUTION_SCHEDULE.RUN_AS_USER_UUID);
            } else {
                list.add(sort.isDesc()
                        ? EXECUTION_SCHEDULE.UUID.desc()
                        : EXECUTION_SCHEDULE.UUID);
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
