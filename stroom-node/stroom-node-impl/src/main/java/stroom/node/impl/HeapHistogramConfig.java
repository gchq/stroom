package stroom.node.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


public class HeapHistogramConfig extends AbstractConfig {

    private final String classNameMatchRegex;
    private final String classNameReplacementRegex;

    public HeapHistogramConfig() {
        classNameMatchRegex = "^stroom\\..*$";
        classNameReplacementRegex =
                "((?<=\\$Proxy)[0-9]+|(?<=\\$\\$)[0-9a-f]+|(?<=\\$\\$Lambda\\$)[0-9]+\\/[0-9]+)";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public HeapHistogramConfig(@JsonProperty("classNameMatchRegex") final String classNameMatchRegex,
                               @JsonProperty("classNameReplacementRegex") final String classNameReplacementRegex) {
        this.classNameMatchRegex = classNameMatchRegex;
        this.classNameReplacementRegex = classNameReplacementRegex;
    }

    @ValidRegex
    @JsonPropertyDescription("A single regex that will be used to filter classes from the heap histogram internal " +
            "statistic based on their name. e.g '^(stroom\\..*)$'. If no value is supplied all classes will " +
            "be included. If a value is supplied only those class names matching the regex will be included.")
    public String getClassNameMatchRegex() {
        return classNameMatchRegex;
    }

    @ValidRegex
    @JsonPropertyDescription("A single regex that will be used to replace all matches in the class name with " +
            "'--REPLACED--'. This is to prevent ids for anonymous inner classes and lambdas from being included " +
            "in the class name. E.g '....DocRefResourceHttpClient$$Lambda$46/1402766141' becomes " +
            "'....DocRefResourceHttpClient$$Lambda$--REPLACED--'. ")
    public String getClassNameReplacementRegex() {
        return classNameReplacementRegex;
    }

    public HeapHistogramConfig withClassNameMatchRegex(final String classNameMatchRegex) {
        return new HeapHistogramConfig(classNameMatchRegex, classNameReplacementRegex);
    }

    @Override
    public String toString() {
        return "HeapHistogramConfig{" +
                "classNameMatchRegex='" + classNameMatchRegex + '\'' +
                ", classNameReplacementRegex='" + classNameReplacementRegex + '\'' +
                '}';
    }
}
