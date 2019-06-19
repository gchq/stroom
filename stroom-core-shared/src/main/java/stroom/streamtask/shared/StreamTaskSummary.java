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

package stroom.streamtask.shared;

import stroom.query.api.v2.DocRef;
import stroom.util.shared.SharedObject;

import java.util.Objects;

public class StreamTaskSummary implements SharedObject {
    private static final long serialVersionUID = 5631193345714122209L;

    private DocRef pipeline;
    private DocRef feed;
    private String priority;
    private TaskStatus status;
    private Long count;

    public StreamTaskSummary() {
    }

    public StreamTaskSummary(final DocRef pipeline, final DocRef feed, final String priority, final TaskStatus status, final Long count) {
        this.pipeline = pipeline;
        this.feed = feed;
        this.priority = priority;
        this.status = status;
        this.count = count;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public DocRef getFeed() {
        return feed;
    }

    public void setFeed(final DocRef feed) {
        this.feed = feed;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(final String priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(final TaskStatus status) {
        this.status = status;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(final Long count) {
        this.count = count;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StreamTaskSummary that = (StreamTaskSummary) o;
        return Objects.equals(pipeline, that.pipeline) &&
                Objects.equals(feed, that.feed) &&
                Objects.equals(priority, that.priority) &&
                status == that.status &&
                Objects.equals(count, that.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipeline, feed, priority, status, count);
    }
}
