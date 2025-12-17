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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class StringCriteria implements Serializable, HasIsConstrained, Clearable, Copyable<StringCriteria> {

    @JsonProperty
    private String string;
    @JsonProperty
    private String stringUpper;
    @JsonProperty
    private MatchStyle matchStyle;
    @JsonProperty
    private boolean caseInsensitive;
    @JsonProperty
    private Boolean matchNull;

    public StringCriteria() {
    }

    public StringCriteria(final String string) {
        this.string = string;
    }

    public StringCriteria(final String string, final MatchStyle matchStyle) {
        this.string = string;
        this.matchStyle = matchStyle;
    }

    @JsonCreator
    public StringCriteria(@JsonProperty("string") final String string,
                          @JsonProperty("stringUpper") final String stringUpper,
                          @JsonProperty("matchStyle") final MatchStyle matchStyle,
                          @JsonProperty("caseInsensitive") final boolean caseInsensitive,
                          @JsonProperty("matchNull") final Boolean matchNull) {
        this.string = string;
        this.stringUpper = stringUpper;
        this.matchStyle = matchStyle;
        this.caseInsensitive = caseInsensitive;
        this.matchNull = matchNull;
    }

    public static List<StringCriteria> convertStringList(final List<String> strings) {
        final List<StringCriteria> criteriaList = new ArrayList<>();

        if (strings != null) {
            for (final String string : strings) {
                criteriaList.add(new StringCriteria(string));
            }
        }
        return criteriaList;
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
    @JsonIgnore
    public boolean isConstrained() {
        return (string != null && (matchStyle == null || string.length() != 0)) || Boolean.TRUE.equals(matchNull);
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
        if (MatchStyle.WildStartAndEnd == matchStyle) {
            return test.contains(compareString);
        }
        return test.equals(compareString);
    }

    @JsonIgnore
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
                case Wild:
                    return rtnString.replace('*', '%');
                case WildStart:
                    return "%" + rtnString.replace('*', '%');
                case WildEnd:
                    return rtnString.replace('*', '%') + "%";
                case WildStartAndEnd:
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StringCriteria that = (StringCriteria) o;
        return caseInsensitive == that.caseInsensitive &&
                Objects.equals(string, that.string) &&
                matchStyle == that.matchStyle &&
                Objects.equals(matchNull, that.matchNull);
    }

    @Override
    public int hashCode() {
        return Objects.hash(string, matchStyle, caseInsensitive, matchNull);
    }

    public enum MatchStyle {
        Wild,
        WildStart,
        WildEnd,
        WildStartAndEnd
    }
}
