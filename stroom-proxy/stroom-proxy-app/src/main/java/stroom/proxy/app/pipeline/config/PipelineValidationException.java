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

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Exception thrown when the reference-message pipeline configuration contains
 * validation errors.
 */
public class PipelineValidationException extends RuntimeException {

    private final PipelineValidationResult validationResult;

    public PipelineValidationException(final PipelineValidationResult validationResult) {
        super(createMessage(validationResult));
        this.validationResult = Objects.requireNonNull(validationResult, "validationResult");
    }

    public PipelineValidationResult getValidationResult() {
        return validationResult;
    }

    private static String createMessage(final PipelineValidationResult validationResult) {
        Objects.requireNonNull(validationResult, "validationResult");

        if (!validationResult.hasErrors()) {
            return "Pipeline validation failed";
        }

        return "Pipeline validation failed with "
               + validationResult.getErrorCount()
               + " error(s): "
               + validationResult.getErrors()
                       .stream()
                       .map(PipelineValidationException::formatIssue)
                       .collect(Collectors.joining("; "));
    }

    private static String formatIssue(final PipelineValidationIssue issue) {
        final StringBuilder builder = new StringBuilder();

        builder.append(issue.code())
                .append(": ")
                .append(issue.message());

        if (issue.stageName() != null) {
            builder.append(" [stage=")
                    .append(issue.stageName().getConfigName())
                    .append(']');
        }

        if (issue.queueName() != null) {
            builder.append(" [queue=")
                    .append(issue.queueName())
                    .append(']');
        }

        if (issue.fileStoreName() != null) {
            builder.append(" [fileStore=")
                    .append(issue.fileStoreName())
                    .append(']');
        }

        return builder.toString();
    }
}
