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

package stroom.sqlstore.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Request body for {@link SqlTemporalStoreResource#applyChanges(ApplyChangesRequest)}.
 *
 * <p>Carries an ordered list of {@link ChangeOperation}s that the server will
 * apply atomically within a single database transaction. The operations are
 * applied strictly in list order — the order in which the user performed them
 * in the UI.</p>
 *
 * <h3>Why a single ordered list?</h3>
 * <p>Using separate "upserts" and "deletions" lists would lose the relative
 * ordering between them. Consider the case where a user deletes an entry for
 * {@code (map, key, t=T)} and then immediately creates a new entry for the
 * same natural key with different data: if deletions were applied first (or
 * last) as a batch, the result would be correct by coincidence only. The single
 * ordered list preserves intent exactly.</p>
 */
@JsonInclude(Include.NON_NULL)
public class ApplyChangesRequest {

    /**
     * Ordered list of operations to apply, in the order the user performed them.
     * Never {@code null}; may be empty (a no-op request).
     */
    @JsonProperty
    private final List<ChangeOperation> operations;

    @JsonCreator
    public ApplyChangesRequest(
            @JsonProperty("operations") final List<ChangeOperation> operations) {
        this.operations = operations != null ? operations : List.of();
    }

    /**
     * Returns the ordered list of operations to apply.
     *
     * @return the operations in user-performed order; never {@code null},
     *         may be empty
     */
    public List<ChangeOperation> getOperations() {
        return operations;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ApplyChangesRequest that = (ApplyChangesRequest) o;
        return Objects.equals(operations, that.operations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operations);
    }

    @Override
    public String toString() {
        return "ApplyChangesRequest{operations=" + operations + '}';
    }
}
