package stroom.util.guice;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FilterInfo {
    private final String name;
    private final String urlPattern;
    private final Map<String, String> initParameters = new HashMap<>();

    public FilterInfo(final String name,
                      final String urlPattern) {
        this.name = name;
        this.urlPattern = urlPattern;
    }

    public FilterInfo addparameter(final String name, final String value) {
        initParameters.put(name, value);
        return this;
    }

    public String getName() {
        return name;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public Map<String, String> getInitParameters() {
        return initParameters;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FilterInfo that = (FilterInfo) o;
        return name.equals(that.name) &&
                urlPattern.equals(that.urlPattern) &&
                initParameters.equals(that.initParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, urlPattern, initParameters);
    }

    @Override
    public String toString() {
        return name;
    }
}
