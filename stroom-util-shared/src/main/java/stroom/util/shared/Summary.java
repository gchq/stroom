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

package stroom.util.shared;

public class Summary implements Marker, TreeRow, SharedObject {
    private static final long serialVersionUID = -2158641083789509554L;

    private Severity severity;
    private int count;
    private int total;
    private Expander expander;

    public Summary() {
        // Default constructor necessary for GWT serialisation.
    }

    public Summary(final Severity severity, final int count, final int total, final Expander expander) {
        this.severity = severity;
        this.count = count;
        this.total = total;
        this.expander = expander;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    public int getCount() {
        return count;
    }

    public int getTotal() {
        return total;
    }

    @Override
    public Expander getExpander() {
        return expander;
    }
}
