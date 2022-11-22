package stroom.dashboard.client.main;

import stroom.query.api.v2.Param;
import stroom.query.api.v2.TimeRange;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class DashboardContext {

    private static final String CORE = "__core__";

    private final Map<String, List<Param>> paramMap = new HashMap<>();
    private TimeRange timeRange;

    public void addParams(final String componentId, final List<Param> params) {
        paramMap.put(componentId, params);
    }

    public void removeParams(final String componentId) {
        paramMap.remove(componentId);
    }

    public List<Param> getCoreParams() {
        return paramMap.get(CORE);
    }

    public void setCoreParams(final List<Param> params) {
        paramMap.put(CORE, params);
    }

    public List<Param> getParams(final String componentId) {
        return paramMap.get(componentId);
    }

    public List<Param> getCombinedParams() {
        Map<String, Param> combined = new HashMap<>();
        addParamsToMap(combined, paramMap.get(CORE));

        for (final Entry<String, List<Param>> entry : paramMap.entrySet()) {
            if (!entry.getKey().equals(CORE)) {
                addParamsToMap(combined, entry.getValue());
            }
        }

        return combined.values().stream().sorted(Comparator.comparing(Param::getKey)).collect(Collectors.toList());
    }

    private void addParamsToMap(final Map<String, Param> combined, final List<Param> params) {
        if (params != null) {
            for (final Param param : params) {
                combined.put(param.getKey(), param);
            }
        }
    }

    public void setTimeRange(final TimeRange timeRange) {
        this.timeRange = timeRange;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }
}
