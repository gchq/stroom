package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.Span;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class PathNode {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final List<String> path;
    @JsonProperty
    private final List<PathNodeList> targets;
    @JsonProperty
    private final List<Span> spans;

    @JsonCreator
    public PathNode(@JsonProperty("uuid") final String uuid,
                    @JsonProperty("name") final String name,
                    @JsonProperty("path") final List<String> path,
                    @JsonProperty("targets") final List<PathNodeList> targets,
                    @JsonProperty("spans") final List<Span> spans) {
        this.uuid = uuid;
        this.name = name;
        this.path = path;
        this.targets = targets == null
                ? new ArrayList<>()
                : new ArrayList<>(targets);
        this.spans = spans == null
                ? new ArrayList<>()
                : new ArrayList<>(spans);
    }

    public PathNode(final String name,
                    final List<String> path) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.path = path;
        this.targets = new ArrayList<>();
        this.spans = new ArrayList<>();
    }

    public PathNode(final String name) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.path = Collections.singletonList(name);
        this.targets = new ArrayList<>();
        this.spans = new ArrayList<>();
    }

    public void addSpan(final Span span) {
        spans.add(span);
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public List<String> getPath() {
        return path;
    }

    public List<PathNodeList> getTargets() {
        return targets;
    }

    public List<Span> getSpans() {
        return spans;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathNode node = (PathNode) o;
        return Objects.equals(name, node.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (final String part : path) {
            for (int i = 0; i < depth * 3; i++) {
                sb.append(' ');
            }
            sb.append(part);
            sb.append("\n");
            depth++;
        }
        return sb.toString();
    }
}
