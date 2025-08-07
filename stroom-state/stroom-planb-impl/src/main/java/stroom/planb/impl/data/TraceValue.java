package stroom.planb.impl.data;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class TraceValue {
//"flags": 768, "name": "/workspace/b9ba234b-97f6-4c39-8db9-93b0ce5f9b67_payload-0_gate-0/command", "kind": 2, "start_time_unix_nano": 1749517172921563334, "end_time_unix_nano": 1749517172928684229, "attributes": {"http.method": "PUT", "http.target": "/workspace/b9ba234b-97f6-4c39-8db9-93b0ce5f9b67_payload-0_gate-0/command", "http.host": "ip-10-1-18-200.eu-west-2.compute.internal:8080", "coral.operation": "PerformCommand", "coral.service": "DeepContentInspectionServiceWorker", "coral.namespace": "com.a2z.dcis.worker", "http.status_code": 200}, "status": {}, "resource": {"service.name": "DCIS-Worker-beta-high-Export", "service.version": "1.0"}, "trace_id_hex": "68478374704d0959e80769ee6e38a15e", "span_id_hex": "35a0708554289fb6", "parent_span_id_hex": "14b7c95a94016fc1", "scope_span_name": "DeepContentInspectionService"}

    private final String name;
    private final Instant startTime;
    private final Instant endTime;
    private final List<TraceAttribute> attributes;
    private final List<TraceEvent> events;
    private final Instant insertTime;

    public TraceValue(final String name,
                      final Instant startTime,
                      final Instant endTime,
                      final List<TraceAttribute> attributes,
                      final List<TraceEvent> events,
                      final Instant insertTime) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attributes = attributes;
        this.events = events;
        this.insertTime = insertTime;
    }

    public String getName() {
        return name;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public List<TraceAttribute> getAttributes() {
        return attributes;
    }

    public List<TraceEvent> getEvents() {
        return events;
    }

    public Instant getInsertTime() {
        return insertTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceValue that = (TraceValue) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime) &&
               Objects.equals(attributes, that.attributes) &&
               Objects.equals(events, that.events) &&
               Objects.equals(insertTime, that.insertTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, startTime, endTime, attributes, events, insertTime);
    }

    @Override
    public String toString() {
        return "TraceValue{" +
               "name='" + name + '\'' +
               ", startTime=" + startTime +
               ", endTime=" + endTime +
               ", attributes=" + attributes +
               ", events=" + events +
               ", insertTime=" + insertTime +
               '}';
    }
}
