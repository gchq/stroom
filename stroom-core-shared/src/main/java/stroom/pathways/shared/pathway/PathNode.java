package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.Span;
import stroom.util.shared.AbstractBuilder;

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
    @JsonProperty
    private final Constraints constraints;

    @JsonCreator
    public PathNode(@JsonProperty("uuid") final String uuid,
                    @JsonProperty("name") final String name,
                    @JsonProperty("path") final List<String> path,
                    @JsonProperty("targets") final List<PathNodeList> targets,
                    @JsonProperty("spans") final List<Span> spans,
                    @JsonProperty("constraints") final Constraints constraints) {
        this.uuid = uuid;
        this.name = name;
        this.path = path;
        this.targets = targets == null
                ? new ArrayList<>()
                : new ArrayList<>(targets);
        this.spans = spans == null
                ? new ArrayList<>()
                : new ArrayList<>(spans);
        this.constraints = constraints;
    }

    public PathNode(final String name,
                    final List<String> path) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.path = path;
        this.targets = new ArrayList<>();
        this.spans = new ArrayList<>();
        this.constraints = null;
    }

    public PathNode(final String name) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.path = Collections.singletonList(name);
        this.targets = new ArrayList<>();
        this.spans = new ArrayList<>();
        this.constraints = null;
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

    public Constraints getConstraints() {
        return constraints;
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<PathNode, Builder> {

        private String uuid;
        private String name;
        private List<String> path;
        private List<PathNodeList> targets;
        private List<Span> spans;
        private Constraints constraints;

        public Builder() {
        }

        public Builder(final PathNode pathNode) {
            this.uuid = pathNode.uuid;
            this.name = pathNode.name;
            this.path = pathNode.path;
            this.targets = pathNode.targets;
            this.spans = pathNode.spans;
            this.constraints = pathNode.constraints;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder path(final List<String> path) {
            this.path = path;
            return self();
        }

        public Builder targets(final List<PathNodeList> targets) {
            this.targets = targets;
            return self();
        }

        public Builder spans(final List<Span> spans) {
            this.spans = spans;
            return self();
        }

        public Builder constraints(final Constraints constraints) {
            this.constraints = constraints;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public PathNode build() {
            return new PathNode(
                    uuid,
                    name,
                    path,
                    targets,
                    spans,
                    constraints);
        }
    }
}
