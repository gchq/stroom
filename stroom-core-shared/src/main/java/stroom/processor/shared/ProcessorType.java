package stroom.processor.shared;

import stroom.docref.HasDisplayValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ProcessorType implements HasDisplayValue {
    PIPELINE("pipelineStreamProcessor"),
    STREAMING_ANALYTIC("streamingAnalyticProcessor");

    private static final Map<String, ProcessorType> map;

    static {
        map = Arrays
                .stream(ProcessorType.values())
                .collect(Collectors.toMap(ProcessorType::getDisplayValue, Function.identity()));
    }

    public static ProcessorType fromDisplayValue(final String displayValue) {
        return map.get(displayValue);
    }

    private final String displayValue;

    ProcessorType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
