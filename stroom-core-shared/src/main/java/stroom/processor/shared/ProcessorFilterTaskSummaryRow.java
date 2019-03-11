/*
 * Copyright 2016 Crown Copyright
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

package stroom.processor.shared;

import stroom.docref.SharedObject;

public class ProcessorFilterTaskSummaryRow implements SharedObject {
    private static final long serialVersionUID = 5631193345714122209L;

    private String pipeline;
    private String feed;
    private int priority;
    private TaskStatus status;
    private long count;

    public ProcessorFilterTaskSummaryRow() {
    }

    public ProcessorFilterTaskSummaryRow(final String pipeline, final String feed, final int priority, final TaskStatus status, final long count) {
        this.pipeline = pipeline;
        this.feed = feed;
        this.priority = priority;
        this.status = status;
        this.count = count;
    }

    public String getPipeline() {
        return pipeline;
    }

    public String getFeed() {
        return feed;
    }

    public int getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public long getCount() {
        return count;
    }
}
