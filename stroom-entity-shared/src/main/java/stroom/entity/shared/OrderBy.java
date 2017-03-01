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

package stroom.entity.shared;

import stroom.util.shared.HasDisplayValue;

import java.io.Serializable;

public class OrderBy implements HasDisplayValue, Serializable {
    private static final long serialVersionUID = 4634337288577415874L;

    private String displayValue;
    private String ejbQL;
    private String sql;
    private boolean caseInsensitive;

    public OrderBy() {
        // Default constructor necessary for GWT serialisation.
    }

    public OrderBy(final String displayValue) {
        this.displayValue = displayValue;
    }

    public OrderBy(final String displayValue, final String ejbQL) {
        this.displayValue = displayValue;
        this.ejbQL = ejbQL;
    }

    public OrderBy(final String displayValue, final String ejbQL, final boolean caseInsensitive) {
        this.displayValue = displayValue;
        this.ejbQL = ejbQL;
        this.caseInsensitive = caseInsensitive;
    }

    public OrderBy(final String displayValue, final String ejbQL, final String sql) {
        this.displayValue = displayValue;
        this.ejbQL = ejbQL;
        this.sql = sql;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public String getEJBQL() {
        return ejbQL;
    }

    public String getSQL() {
        return sql;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof OrderBy)) {
            return false;
        }
        return this.getDisplayValue().equals(((OrderBy) o).getDisplayValue());
    }

    @Override
    public int hashCode() {
        return getDisplayValue().hashCode();
    }
}
