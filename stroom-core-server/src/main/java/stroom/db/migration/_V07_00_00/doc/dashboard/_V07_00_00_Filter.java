/*
 * Copyright 2017 Crown Copyright
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

package stroom.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_EqualsBuilder;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_HashCodeBuilder;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_ToStringBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"includes", "excludes"})
@XmlRootElement(name = "filter")
@XmlType(name = "Filter", propOrder = {"includes", "excludes"})
public class _V07_00_00_Filter implements Serializable {
    private static final long serialVersionUID = 7327802315955158337L;

    @XmlElement(name = "includes")
    private String includes;
    @XmlElement(name = "excludes")
    private String excludes;

    public _V07_00_00_Filter() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_Filter(String includes, String excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    public String getIncludes() {
        return includes;
    }

    public void setIncludes(final String includes) {
        this.includes = includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public void setExcludes(final String excludes) {
        this.excludes = excludes;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof _V07_00_00_Filter)) {
            return false;
        }

        final _V07_00_00_Filter filter = (_V07_00_00_Filter) o;
        final _V07_00_00_EqualsBuilder builder = new _V07_00_00_EqualsBuilder();
        builder.append(includes, filter.includes);
        builder.append(excludes, filter.excludes);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final _V07_00_00_HashCodeBuilder builder = new _V07_00_00_HashCodeBuilder();
        builder.append(includes);
        builder.append(excludes);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        final _V07_00_00_ToStringBuilder builder = new _V07_00_00_ToStringBuilder();
        builder.append("includes", includes);
        builder.append("excludes", excludes);
        return builder.toString();
    }
}
