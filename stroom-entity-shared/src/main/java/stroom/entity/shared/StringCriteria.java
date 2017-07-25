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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

public class StringCriteria implements Serializable, HasIsConstrained, Clearable, Copyable<StringCriteria> {
    private static final long serialVersionUID = 4737939969786534908L;

    public enum MatchStyle {
        WildEnd, WildStandAndEnd
    }

    private String string;
    private String stringUpper;
    private MatchStyle matchStyle = null;
    private boolean caseInsensitive;
    private Boolean matchNull = null;

    public StringCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public StringCriteria(final String string) {
        this.string = string;
    }

    public StringCriteria(final String string, final MatchStyle matchStyle) {
        this.string = string;
        this.matchStyle = matchStyle;
    }

    @Override
    public void clear() {
        string = null;
        matchStyle = null;
    }

    public String getString() {
        return string;
    }

    public void setString(final String string) {
        this.string = string;
        if (string == null) {
            stringUpper = null;
        } else {
            stringUpper = string.toUpperCase();
        }
    }

    public MatchStyle getMatchStyle() {
        return matchStyle;
    }

    public void setMatchStyle(final MatchStyle matchStyle) {
        this.matchStyle = matchStyle;
    }

    @Override
    public boolean isConstrained() {
        if (string == null) {
            return false;
        }
        if (matchStyle == null) {
            return true;
        }
        return string.length() != 0;
    }

    public boolean isMatch(String test) {
        String compareString = string;
        if (isCaseInsensitive() && test != null) {
            test = test.toUpperCase();
            compareString = stringUpper;
        }
        if (!isConstrained()) {
            return true;
        }
        if (MatchStyle.WildEnd == matchStyle) {
            return test.startsWith(compareString);
        }
        if (MatchStyle.WildStandAndEnd == matchStyle) {
            return test.contains(compareString);
        }
        return test.equals(compareString);
    }

    public String getMatchString() {
        if (string == null) {
            return null;
        }
        String rtnString = string;
        if (isCaseInsensitive()) {
            rtnString = string.toUpperCase();
        }
        if (matchStyle != null) {
            switch (matchStyle) {
                case WildEnd:
                    return rtnString.replace('*', '%') + "%";
                case WildStandAndEnd:
                    return "%" + rtnString.replace('*', '%') + "%";
                default:
                    return rtnString;
            }
        } else {
            return rtnString;
        }
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    public void setCaseInsensitive(final boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    public Boolean getMatchNull() {
        return matchNull;
    }

    public void setMatchNull(final Boolean matchNull) {
        this.matchNull = matchNull;
    }

    @Override
    public void copyFrom(final StringCriteria other) {
        this.string = other.string;
        this.stringUpper = other.stringUpper;
        this.matchStyle = other.matchStyle;
        this.caseInsensitive = other.caseInsensitive;
        this.matchNull = other.matchNull;
    }

    @Override
    public String toString() {
        return getMatchString();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(string);
        builder.append(matchStyle);
        builder.append(matchNull);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof StringCriteria)) {
            return false;
        }

        final StringCriteria stringCriteria = (StringCriteria) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(string, stringCriteria.string);
        builder.append(matchStyle, stringCriteria.matchStyle);
        builder.append(matchNull, stringCriteria.matchNull);
        return builder.isEquals();
    }

    public static List<StringCriteria> convertStringList(List<String> strings) {
        List<StringCriteria> criteriaList = new ArrayList<>();

        if (strings != null) {
            for (String string : strings) {
                criteriaList.add(new StringCriteria(string));
            }
        }
        return criteriaList;
    }
}
