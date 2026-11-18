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
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// Reads processing tasks a page at a time, newest first, keeping those the user may see, within a limit on the
/// rows read and on the tasks kept. Processing tasks need the Manage Processors permission, which callers check
/// first.
public final class ProcessorTaskFinder {

    /// The most task rows read at a time.
    public static final int PAGE_SIZE = 1_000;

    private ProcessorTaskFinder() {
        // Static methods only.
    }

    /// @param service    The processing task service.
    /// @param expression The search.
    /// @param maxRows    The most rows to read.
    /// @param maxVisible The most tasks to keep.
    /// @param canSee     True for a task the user may see.
    /// @return The tasks read.
    public static Tasks find(final ProcessorTaskService service,
                             final ExpressionOperator expression,
                             final int maxRows,
                             final int maxVisible,
                             final Predicate<ProcessorTask> canSee) {
        final List<ProcessorTask> visible = new ArrayList<>();
        int read = 0;
        boolean moreRows = false;
        boolean moreVisible = false;
        reading:
        while (read < maxRows) {
            ToolDeadline.check();
            final int length = Math.min(PAGE_SIZE, maxRows - read);
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
            // One more than the page, to tell if there are more.
            criteria.setPageRequest(new PageRequest(read, length + 1));
            criteria.setSort(ProcessorTaskFields.FIELD_ID, true, false);
            final List<ProcessorTask> page = NullSafe.list(NullSafe.get(service.find(criteria),
                    ResultPage::getValues));
            final List<ProcessorTask> rows = page.subList(0, Math.min(page.size(), length));
            read += rows.size();
            for (final ProcessorTask task : rows) {
                if (canSee.test(task)) {
                    if (visible.size() >= maxVisible) {
                        // Only a task the user may see makes "more".
                        moreVisible = true;
                        break reading;
                    }
                    visible.add(task);
                }
            }
            moreRows = page.size() > length;
            if (!moreRows) {
                break;
            }
        }
        return new Tasks(visible, moreRows && !moreVisible && read >= maxRows, moreVisible);
    }


    // --------------------------------------------------------------------------------


    /// Tasks read, newest first.
    ///
    /// @param visible Those the user may see.
    /// @param capped  True if reading stopped at the most rows to read, with more to read.
    /// @param more    True if there were more the user may see than were kept.
    public record Tasks(List<ProcessorTask> visible, boolean capped, boolean more) {

    }
}
