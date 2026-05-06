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

package stroom.proxy.app.pipeline.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Immutable validation result for the reference-message pipeline configuration.
 */
public class PipelineValidationResult {

    private static final PipelineValidationResult VALID = new PipelineValidationResult(List.of());

    private final List<PipelineValidationIssue> issues;

    public PipelineValidationResult(final Collection<PipelineValidationIssue> issues) {
        Objects.requireNonNull(issues, "issues");
        this.issues = List.copyOf(issues);
    }

    public static PipelineValidationResult valid() {
        return VALID;
    }

    public static PipelineValidationResult of(final Collection<PipelineValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return valid();
        }
        return new PipelineValidationResult(issues);
    }

    public List<PipelineValidationIssue> getIssues() {
        return issues;
    }

    public Stream<PipelineValidationIssue> stream() {
        return issues.stream();
    }

    public List<PipelineValidationIssue> getErrors() {
        return issues.stream()
                .filter(PipelineValidationIssue::isError)
                .toList();
    }

    public List<PipelineValidationIssue> getWarnings() {
        return issues.stream()
                .filter(PipelineValidationIssue::isWarning)
                .toList();
    }

    public boolean isValid() {
        return !hasErrors();
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public boolean hasErrors() {
        return issues.stream()
                .anyMatch(PipelineValidationIssue::isError);
    }

    public boolean hasWarnings() {
        return issues.stream()
                .anyMatch(PipelineValidationIssue::isWarning);
    }

    public int getIssueCount() {
        return issues.size();
    }

    public int getErrorCount() {
        return (int) issues.stream()
                .filter(PipelineValidationIssue::isError)
                .count();
    }

    public int getWarningCount() {
        return (int) issues.stream()
                .filter(PipelineValidationIssue::isWarning)
                .count();
    }

    public void throwIfInvalid() {
        if (hasErrors()) {
            throw new PipelineValidationException(this);
        }
    }

    @Override
    public String toString() {
        return "PipelineValidationResult{" +
               "issues=" + issues +
               '}';
    }
}
