/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.sqlstore.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Result of a {@link SqlTemporalStoreResource#applyChanges(ApplyChangesRequest)} call.
 *
 * <p>Because {@code applyChanges} executes all operations atomically within a
 * single database transaction, the result is binary: either all operations
 * succeeded or none did. There is no partial-success state.</p>
 *
 * <h3>Client error handling</h3>
 * <p>When {@link #success} is {@code false} the client should:</p>
 * <ol>
 *   <li>Display {@link #errorMessage} as a top-level alert to the user.</li>
 *   <li>Clear its pending-changes buffer (since the server rolled back, the
 *       pending changes are now stale).</li>
 *   <li>Reload all panels (Fact List, Time List, canvas) from the server to
 *       restore a consistent view.</li>
 * </ol>
 */
@JsonInclude(Include.NON_NULL)
public class ApplyChangesResult {

    /**
     * {@code true} if all operations were applied successfully; {@code false}
     * if any operation failed and the transaction was rolled back.
     */
    @JsonProperty
    private final boolean success;

    /**
     * A human-readable description of the error that caused the rollback.
     * {@code null} when {@link #success} is {@code true}.
     */
    @JsonProperty
    private final String errorMessage;

    @JsonCreator
    public ApplyChangesResult(
            @JsonProperty("success") final Boolean success,
            @JsonProperty("errorMessage") final String errorMessage) {
        // An absent success flag is treated as a failure
        this.success = NullSafe.isTrue(success);
        this.errorMessage = errorMessage;
    }

    /**
     * Returns {@code true} if all operations were applied successfully.
     *
     * @return {@code true} on success; {@code false} if the transaction was
     *         rolled back
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the error message from a failed apply operation.
     *
     * @return the error description; {@code null} when {@link #isSuccess()}
     *         is {@code true}
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ApplyChangesResult that = (ApplyChangesResult) o;
        return success == that.success
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errorMessage);
    }

    @Override
    public String toString() {
        return "ApplyChangesResult{success=" + success
                + (errorMessage != null ? ", errorMessage='" + errorMessage + '\'' : "")
                + '}';
    }
}
