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

package stroom.dashboard.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ValidateExpressionResult {

    @JsonProperty
    private final boolean ok;
    @JsonProperty
    private final String string;
    @JsonProperty
    private final boolean groupBy;

    @JsonCreator
    public ValidateExpressionResult(@JsonProperty("ok") final boolean ok,
                                    @JsonProperty("string") final String string,
                                    @JsonProperty("groupBy") final boolean groupBy) {
        this.ok = ok;
        this.string = string;
        this.groupBy = groupBy;
    }

    public static ValidateExpressionResult ok() {
        return new ValidateExpressionResult(true, null, false);
    }

    public static ValidateExpressionResult ok(final boolean isGroup) {
        return new ValidateExpressionResult(true, null, isGroup);
    }

    public static ValidateExpressionResult failed(final Throwable throwable) {
        return new ValidateExpressionResult(
                false,
                NullSafe.get(throwable, Throwable::getMessage),
                false);
    }

    public static ValidateExpressionResult failed(final Throwable throwable, final boolean isGroup) {
        return new ValidateExpressionResult(
                false,
                NullSafe.get(throwable, Throwable::getMessage),
                isGroup);
    }

    public boolean isOk() {
        return ok;
    }

    public String getString() {
        return string;
    }

    public boolean isGroupBy() {
        return groupBy;
    }

    @Override
    public String toString() {
        return "ValidateExpressionResult{" +
                "ok=" + ok +
                ", string='" + string + '\'' +
                ", groupBy=" + groupBy +
                '}';
    }
}
