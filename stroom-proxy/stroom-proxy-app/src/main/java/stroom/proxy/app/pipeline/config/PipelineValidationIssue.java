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

import stroom.proxy.app.pipeline.runtime.PipelineStageName;

import java.util.Objects;

/**
 * A single validation issue found in the reference-message pipeline configuration.
 * <p>
 * The pipeline validator should use this model to report configuration problems
 * in a structured way without immediately throwing an exception. Callers can then
 * decide whether to reject boot, log warnings, or present the issues in tests or
 * diagnostics.
 * </p>
 *
 * @param severity The issue severity.
 * @param code A stable issue code suitable for tests and diagnostics.
 * @param message A human-readable message.
 * @param stageName The stage associated with the issue, if any.
 * @param queueName The queue associated with the issue, if any.
 * @param fileStoreName The file store associated with the issue, if any.
 */
public record PipelineValidationIssue(
        Severity severity,
        String code,
        String message,
        PipelineStageName stageName,
        String queueName,
        String fileStoreName) {

    public PipelineValidationIssue {
        severity = Objects.requireNonNull(severity, "severity");
        code = requireNonBlank(code, "code");
        message = requireNonBlank(message, "message");
        queueName = normaliseOptional(queueName);
        fileStoreName = normaliseOptional(fileStoreName);
    }

    public static PipelineValidationIssue error(final String code,
                                                final String message) {
        return new PipelineValidationIssue(
                Severity.ERROR,
                code,
                message,
                null,
                null,
                null);
    }

    public static PipelineValidationIssue errorForStage(final PipelineStageName stageName,
                                                        final String code,
                                                        final String message) {
        return new PipelineValidationIssue(
                Severity.ERROR,
                code,
                message,
                Objects.requireNonNull(stageName, "stageName"),
                null,
                null);
    }

    public static PipelineValidationIssue errorForQueue(final String queueName,
                                                        final String code,
                                                        final String message) {
        return new PipelineValidationIssue(
                Severity.ERROR,
                code,
                message,
                null,
                requireNonBlank(queueName, "queueName"),
                null);
    }

    public static PipelineValidationIssue errorForFileStore(final String fileStoreName,
                                                           final String code,
                                                           final String message) {
        return new PipelineValidationIssue(
                Severity.ERROR,
                code,
                message,
                null,
                null,
                requireNonBlank(fileStoreName, "fileStoreName"));
    }

    public static PipelineValidationIssue errorForStageQueue(final PipelineStageName stageName,
                                                            final String queueName,
                                                            final String code,
                                                            final String message) {
        return new PipelineValidationIssue(
                Severity.ERROR,
                code,
                message,
                Objects.requireNonNull(stageName, "stageName"),
                requireNonBlank(queueName, "queueName"),
                null);
    }

    public static PipelineValidationIssue errorForStageFileStore(final PipelineStageName stageName,
                                                                 final String fileStoreName,
                                                                 final String code,
                                                                 final String message) {
        return new PipelineValidationIssue(
                Severity.ERROR,
                code,
                message,
                Objects.requireNonNull(stageName, "stageName"),
                null,
                requireNonBlank(fileStoreName, "fileStoreName"));
    }

    public static PipelineValidationIssue warning(final String code,
                                                  final String message) {
        return new PipelineValidationIssue(
                Severity.WARNING,
                code,
                message,
                null,
                null,
                null);
    }

    public static PipelineValidationIssue warningForStage(final PipelineStageName stageName,
                                                          final String code,
                                                          final String message) {
        return new PipelineValidationIssue(
                Severity.WARNING,
                code,
                message,
                Objects.requireNonNull(stageName, "stageName"),
                null,
                null);
    }

    public static PipelineValidationIssue warningForQueue(final String queueName,
                                                          final String code,
                                                          final String message) {
        return new PipelineValidationIssue(
                Severity.WARNING,
                code,
                message,
                null,
                requireNonBlank(queueName, "queueName"),
                null);
    }

    public static PipelineValidationIssue warningForFileStore(final String fileStoreName,
                                                             final String code,
                                                             final String message) {
        return new PipelineValidationIssue(
                Severity.WARNING,
                code,
                message,
                null,
                null,
                requireNonBlank(fileStoreName, "fileStoreName"));
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    public boolean hasStage() {
        return stageName != null;
    }

    public boolean hasQueue() {
        return queueName != null;
    }

    public boolean hasFileStore() {
        return fileStoreName != null;
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public enum Severity {
        ERROR,
        WARNING
    }
}
