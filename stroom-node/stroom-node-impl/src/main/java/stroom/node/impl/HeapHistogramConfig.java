package stroom.node.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;

import javax.inject.Singleton;

@Singleton
public class HeapHistogramConfig extends AbstractConfig {
    private String classNameMatchRegex = "^stroom\\..*$";
    private String classNameReplacementRegex =
        "((?<=\\$Proxy)[0-9]+|(?<=\\$\\$)[0-9a-f]+|(?<=\\$\\$Lambda\\$)[0-9]+\\/[0-9]+)";

    @ValidRegex
    @JsonPropertyDescription("A single regex that will be used to filter classes from the heap histogram internal " +
        "statistic based on their name. e.g '^(stroom\\..*)$'. If no value is supplied all classes will be included. " +
        "If a value is supplied only those class names matching the regex will be included.")
    public String getClassNameMatchRegex() {
        return classNameMatchRegex;
    }

    @SuppressWarnings("unused")
    public void setClassNameMatchRegex(final String classNameMatchRegex) {
        this.classNameMatchRegex = classNameMatchRegex;
    }

    @ValidRegex
    @JsonPropertyDescription("A single regex that will be used to replace all matches in the class name with " +
        "'--REPLACED--'. This is to prevent ids for anonymous inner classes and lambdas from being included in the " +
        "class name. E.g '....DocRefResourceHttpClient$$Lambda$46/1402766141' becomes " +
        "'....DocRefResourceHttpClient$$Lambda$--REPLACED--'. ")
    public String getClassNameReplacementRegex() {
        return classNameReplacementRegex;
    }

    @SuppressWarnings("unused")
    public void setClassNameReplacementRegex(final String classNameReplacementRegex) {
        this.classNameReplacementRegex = classNameReplacementRegex;
    }
}
