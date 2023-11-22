package stroom.docref;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"matchType", "caseSensitive", "pattern"})
@JsonInclude(Include.NON_NULL)
public class StringMatch {

    @JsonProperty
    private final MatchType matchType;
    @JsonProperty
    private final boolean caseSensitive;
    @JsonProperty
    private final String pattern;

    public static StringMatch any() {
        return new StringMatch(MatchType.ANY, false, null);
    }

    public static StringMatch createNull() {
        return new StringMatch(MatchType.NULL, false, null);
    }

    public static StringMatch nonNull() {
        return new StringMatch(MatchType.NON_NULL, false, null);
    }

    public static StringMatch blank() {
        return new StringMatch(MatchType.BLANK, false, null);
    }

    public static StringMatch nullOrBlank() {
        return new StringMatch(MatchType.NULL_OR_BLANK, false, null);
    }

    public static StringMatch contains(final String pattern) {
        return new StringMatch(MatchType.CONTAINS, false, pattern);
    }

    public static StringMatch contains(final String pattern, final boolean caseSensitive) {
        return new StringMatch(MatchType.CONTAINS, caseSensitive, pattern);
    }

    public static StringMatch equals(final String pattern) {
        return new StringMatch(MatchType.EQUALS, false, pattern);
    }

    public static StringMatch equals(final String pattern, final boolean caseSensitive) {
        return new StringMatch(MatchType.EQUALS, caseSensitive, pattern);
    }

    public static StringMatch regex(final String pattern) {
        return new StringMatch(MatchType.REGEX, false, pattern);
    }

    public static StringMatch regex(final String pattern, final boolean caseSensitive) {
        return new StringMatch(MatchType.REGEX, caseSensitive, pattern);
    }

    @JsonCreator
    public StringMatch(@JsonProperty("matchType") final MatchType matchType,
                       @JsonProperty("caseSensitive") final boolean caseSensitive,
                       @JsonProperty("pattern") final String pattern) {
        this.matchType = matchType;
        this.caseSensitive = caseSensitive;
        this.pattern = pattern;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StringMatch)) {
            return false;
        }
        final StringMatch stringMatch = (StringMatch) o;
        return caseSensitive == stringMatch.caseSensitive &&
                matchType == stringMatch.matchType &&
                Objects.equals(pattern, stringMatch.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchType, caseSensitive, pattern);
    }

    public enum MatchType {
        ANY,
        NULL,
        NON_NULL,
        BLANK,
        EMPTY,
        NULL_OR_BLANK,
        NULL_OR_EMPTY,
        CONTAINS,
        EQUALS,
        STARTS_WITH,
        ENDS_WITH,
        REGEX
    }
}
