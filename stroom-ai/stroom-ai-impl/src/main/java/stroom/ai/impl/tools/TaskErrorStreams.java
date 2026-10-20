/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.processor.shared.ProcessorTask;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The Error streams of processing tasks: the Error streams whose parent is a task's input stream, written by
/// that task (or, where a stream doesn't say which task wrote it, by the task's pipeline), with their counts.
///
/// They are found with one search per [#LOOKUP_CHUNK] tasks (Parent Id in the input streams, and Type Error),
/// never a search per task. They are found through [MetaService], so only those of feeds the user may view.
///
/// @param byTask       Each task's Error streams, by task id.
/// @param lookupFailed True if they couldn't all be looked for.
record TaskErrorStreams(Map<Long, List<MetaRow>> byTask, boolean lookupFailed) {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskErrorStreams.class);

    /// The most input streams whose Error streams are looked for in one search.
    static final int LOOKUP_CHUNK = 500;
    /// The most Error streams found for each input stream, on average: one is written each time a stream is
    /// processed.
    static final int PER_STREAM = 4;

    /// @param metaService Finds streams, as the user.
    /// @param tasks       The tasks.
    /// @return Their Error streams.
    static TaskErrorStreams find(final MetaService metaService, final List<ProcessorTask> tasks) {
        final Map<Long, List<MetaRow>> byParent = new HashMap<>();
        final List<Long> parents = tasks.stream()
                .map(ProcessorTask::getMetaId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        boolean failed = false;
        for (int start = 0; start < parents.size(); start += LOOKUP_CHUNK) {
            ToolDeadline.check();
            final List<Long> chunk = parents.subList(start, Math.min(parents.size(), start + LOOKUP_CHUNK));
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addTerm(MetaFields.PARENT_ID.getFldName(), Condition.IN, ProcessorTasksTool.ids(chunk))
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                    .build();
            final FindMetaCriteria criteria = new FindMetaCriteria(expression);
            criteria.setPageRequest(new PageRequest(0, chunk.size() * PER_STREAM));
            try {
                NullSafe.list(NullSafe.get(metaService.findRows(criteria), ResultPage::getValues)).stream()
                        .filter(row -> row.getMeta() != null && row.getMeta().getParentMetaId() != null)
                        .forEach(row -> byParent.computeIfAbsent(row.getMeta().getParentMetaId(),
                                k -> new ArrayList<>()).add(row));
            } catch (final ToolTimeoutException e) {
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to find the Error streams of the tasks", e);
                failed = true;
            }
        }
        final Map<Long, List<MetaRow>> byTask = new HashMap<>();
        for (final ProcessorTask task : tasks) {
            byTask.put(task.getId(), NullSafe.list(byParent.get(task.getMetaId())).stream()
                    .filter(row -> row.getMeta().getProcessorTaskId() != null
                            ? row.getMeta().getProcessorTaskId() == task.getId()
                            : task.getProcessorFilter() != null
                              && Objects.equals(row.getMeta().getPipelineUuid(),
                                    task.getProcessorFilter().getPipelineUuid()))
                    .toList());
        }
        return new TaskErrorStreams(byTask, failed);
    }

    /// @param task A task.
    /// @return Its Error streams.
    List<MetaRow> of(final ProcessorTask task) {
        return NullSafe.list(byTask.get(task.getId()));
    }

    /// @return The ids of all the Error streams found.
    Set<Long> ids() {
        final Set<Long> ids = new LinkedHashSet<>();
        byTask.values().forEach(rows -> rows.forEach(row -> ids.add(row.getMeta().getId())));
        return ids;
    }

    /// @param task A task.
    /// @return Its Error streams in words, as links with their counts, e.g. `Error stream(s) [stream 9](…) (1
    /// fatal, 3 error(s), 0 warning(s))`, or that it has none. A Failed task's run threw, so it usually has none,
    /// which is said so as not to suggest nothing went wrong.
    String describe(final ProcessorTask task) {
        final List<MetaRow> rows = of(task);
        if (rows.isEmpty()) {
            if (lookupFailed) {
                return "Error streams unknown";
            }
            return switch (task.getStatus()) {
                case FAILED -> "no Error stream (a Failed task's run threw, so it usually writes none; that "
                               + "doesn't mean nothing went wrong)";
                case COMPLETE -> "no Error stream";
                case null, default -> "no Error stream yet";
            };
        }
        return "Error stream(s) " + String.join(", ", rows.stream()
                .map(row -> ProcessorTasksTool.streamLink(row.getMeta().getId()) + " ("
                            + MetaCounts.of(List.of(row)).describe() + ")")
                .toList());
    }
}
