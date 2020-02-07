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

package stroom.dashboard.shared;

public class ValidateExpressionResult {
    private boolean ok;
    private String string;

    public ValidateExpressionResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public ValidateExpressionResult(final boolean ok, final String string) {
        this.ok = ok;
        this.string = string;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(final boolean ok) {
        this.ok = ok;
    }

    public String getString() {
        return string;
    }

    public void setString(final String string) {
        this.string = string;
    }
}
