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

package stroom.proxy.app.handler;

import stroom.proxy.app.DownstreamHostConfig;
import stroom.util.io.PathCreator;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.validation.ValidDirectoryPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public final class ForwardFileConfig
        extends AbstractConfig
        implements IsProxyConfig, ForwarderConfig {

    public static final String PROP_NAME_SUB_PATH_TEMPLATE = "subPathTemplate";
    public static final String PROP_NAME_ATOMIC_MOVE_ENABLED = "atomicMoveEnabled";
    public static final TemplatingMode DEFAULT_TEMPLATING_MODE = TemplatingMode.REPLACE_UNKNOWN_PARAMS;

    private static final String DEFAULT_SUB_PATH_TEMPLATE = "${year}${month}${day}/${feed}";
    private static final boolean DEFAULT_IS_ATOMIC_MOVE_ENABLED = true;
    private static final LivenessCheckMode DEFAULT_LIVENESS_CHECK_MODE = LivenessCheckMode.READ;
    public static final boolean DEFAULT_IS_ENABLED = true;
    public static final boolean DEFAULT_IS_INSTANT = false;

    private final boolean enabled;
    private final boolean instant;
    private final String name;
    private final String path;
    private final PathTemplateConfig subPathTemplate;
    private final ForwardFileQueueConfig forwardQueueConfig;
    private final String livenessCheckPath;
    private final LivenessCheckMode livenessCheckMode;
    private final boolean atomicMoveEnabled;

    public ForwardFileConfig() {
        enabled = DEFAULT_IS_ENABLED;
        instant = DEFAULT_IS_INSTANT;
        name = null;
        path = null;
        subPathTemplate = null;
        forwardQueueConfig = new ForwardFileQueueConfig();
        livenessCheckPath = null;
        livenessCheckMode = DEFAULT_LIVENESS_CHECK_MODE;
        atomicMoveEnabled = DEFAULT_IS_ATOMIC_MOVE_ENABLED;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardFileConfig(@JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("instant") final boolean instant,
                             @JsonProperty("name") final String name,
                             @JsonProperty("path") final String path,
                             @JsonProperty(PROP_NAME_SUB_PATH_TEMPLATE) final PathTemplateConfig subPathTemplate,
                             @JsonProperty("queue") final ForwardFileQueueConfig forwardQueueConfig,
                             @JsonProperty("livenessCheckPath") final String livenessCheckPath,
                             @JsonProperty("livenessCheckMode") final LivenessCheckMode livenessCheckMode,
                             @JsonProperty(PROP_NAME_ATOMIC_MOVE_ENABLED) final Boolean atomicMoveEnabled) {
        this.enabled = enabled;
        this.instant = instant;
        this.name = name;
        this.path = path;
        this.subPathTemplate = Objects.requireNonNullElse(subPathTemplate, PathTemplateConfig.DISABLED);
        this.forwardQueueConfig = Objects.requireNonNullElseGet(forwardQueueConfig, ForwardFileQueueConfig::new);
        this.livenessCheckPath = livenessCheckPath;
        this.livenessCheckMode = Objects.requireNonNullElse(livenessCheckMode, DEFAULT_LIVENESS_CHECK_MODE);
        this.atomicMoveEnabled = Objects.requireNonNullElse(atomicMoveEnabled, DEFAULT_IS_ATOMIC_MOVE_ENABLED);
    }

    private ForwardFileConfig(final Builder builder) {
        enabled = builder.enabled;
        instant = builder.instant;
        name = builder.name;
        path = builder.path;
        subPathTemplate = builder.subPathTemplate;
        forwardQueueConfig = builder.forwardQueueConfig;
        livenessCheckPath = builder.livenessCheckPath;
        livenessCheckMode = builder.livenessCheckMode;
        atomicMoveEnabled = builder.atomicMoveEnabled;
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @Override
    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("Should data be forwarded instantly during the receipt process, i.e. must we" +
                             " successfully forward before returning a success response to the sender.")
    public boolean isInstant() {
        return instant;
    }

    @Override
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The unique name of the destination (across all file/http forward destinations. " +
                             "The name is used in the directories on the file system, so do not change the name " +
                             "once proxy has processed data. Must be provided.")
    public String getName() {
        return name;
    }

    /**
     * The string to use for the destination path.
     */
    @NotNull
    @ValidDirectoryPath(ensureExistence = true)
    @JsonPropertyDescription("The string to use for the destination path.")
    @JsonProperty
    public String getPath() {
        return path;
    }

    /**
     * The template to create subdirectories within the 'path' directory.
     * The default value is <code>${year}${month}${day}/${feed}</code>
     * Must be a relative path.
     */
    @NotNull
    @JsonPropertyDescription("The templated relative sub-path of path. " +
                             "The default path template is '" + PathTemplateConfig.DATE_AND_FEED_TEMPLATE + "'. " +
                             "Cannot be an absolute path and must resolve to a descendant of path.")
    @JsonProperty
    public PathTemplateConfig getSubPathTemplate() {
        return subPathTemplate;
    }

    @NotNull
    @Override
    @JsonProperty("queue")
    @JsonPropertyDescription("Adds multi-threading and retry control to this forwarder. Can be set to null " +
                             "for a local file forwarder, but should be populated if the file forwarder is " +
                             "forwarding to a remote file system that may fail. Defaults to null as a " +
                             "local file forwarder is assumed.")
    public ForwardFileQueueConfig getForwardQueueConfig() {
        return forwardQueueConfig;
    }

    @JsonIgnore
    @Override
    public String getDestinationDescription(final DownstreamHostConfig ignored,
                                            final PathCreator pathCreator) {
        final String appPath = pathCreator.toAppPath(path).toString();
        return subPathTemplate.hasPathTemplate()
                ? appPath + "/" + subPathTemplate.getPathTemplate()
                : appPath;
    }

    @JsonIgnore
    boolean hasSubPathTemplate() {
        return subPathTemplate != null
               && subPathTemplate.hasPathTemplate();
    }

    @JsonProperty
    @JsonPropertyDescription("The path to use for regular liveness checking of this forward destination. " +
                             "If null, empty or if the 'queue' property is not configured, then no liveness check " +
                             "will be performed and the destination will be " +
                             "assumed to be healthy. If livenessCheckMode is READ, livenessCheckPath can be a " +
                             "directory or a file and stroom-proxy will attempt to check it can read the " +
                             "file/directory. If livenessCheckMode is WRITE, then livenessCheckPath must be a " +
                             "file and stroom-proxy will attempt to touch that file. It is " +
                             "only recommended to set this property for a remote file system where " +
                             "connection issues may be likely. If it is a relative path, it will be assumed " +
                             "to be relative to 'path'.")
    public String getLivenessCheckPath() {
        return livenessCheckPath;
    }

    @JsonPropertyDescription(
            "The type of liveness check to perform (READ|WRITE). " +
            "READ will attempt to read the file/dir specified in livenessCheckPath. " +
            "WRITE will attempt to touch the file specified in livenessCheckPath.")
    public LivenessCheckMode getLivenessCheckMode() {
        return livenessCheckMode;
    }

    @JsonPropertyDescription(
            "Stroom-Proxy will attempt to move files onto the forward destination using an atomic move. " +
            "This ensures that the move does not happen more than once. If an atomic move is not possible, " +
            "e.g. the destination is a remote file system that does not support an atomic move, then it will " +
            "fall back to a non-atomic move with the risk of it happening more than once. If you see warnings " +
            "in the logs or know the file system will not support atomic moves then set this to false.")
    public boolean isAtomicMoveEnabled() {
        return atomicMoveEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ForwardFileConfig that = (ForwardFileConfig) o;
        return enabled == that.enabled
               && instant == that.instant
               && Objects.equals(name, that.name)
               && Objects.equals(path, that.path)
               && Objects.equals(subPathTemplate, that.subPathTemplate)
               && Objects.equals(forwardQueueConfig, that.forwardQueueConfig)
               && Objects.equals(livenessCheckPath, that.livenessCheckPath)
               && livenessCheckMode == that.livenessCheckMode
               && atomicMoveEnabled == that.atomicMoveEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                instant,
                name,
                path,
                subPathTemplate,
                forwardQueueConfig,
                livenessCheckPath,
                livenessCheckMode,
                atomicMoveEnabled);
    }

    @Override
    public String toString() {
        return "ForwardFileConfig{" +
               "enabled=" + enabled +
               ", instant=" + instant +
               ", name='" + name + '\'' +
               ", path='" + path + '\'' +
               ", subPathTemplate='" + subPathTemplate + '\'' +
               ", forwardQueueConfig=" + forwardQueueConfig +
               ", livenessCheckPath='" + livenessCheckPath + '\'' +
               ", livenessCheckMode=" + livenessCheckMode +
               ", atomicMoveEnabled=" + atomicMoveEnabled +
               '}';
    }

    public static Builder builder() {
        return builder(new ForwardFileConfig());
    }

    public static Builder builder(final ForwardFileConfig copy) {
        final Builder builder = new Builder();
        builder.enabled = copy.isEnabled();
        builder.instant = copy.isInstant();
        builder.name = copy.getName();
        builder.path = copy.getPath();
        builder.subPathTemplate = copy.getSubPathTemplate();
        builder.forwardQueueConfig = copy.getForwardQueueConfig();
        builder.livenessCheckPath = copy.getLivenessCheckPath();
        builder.livenessCheckMode = copy.getLivenessCheckMode();
        builder.atomicMoveEnabled = copy.isAtomicMoveEnabled();
        return builder;
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String livenessCheckPath;
        private LivenessCheckMode livenessCheckMode;
        private boolean atomicMoveEnabled;
        private boolean enabled;
        private boolean instant;
        private String name;
        private String path;
        private PathTemplateConfig subPathTemplate;
        private ForwardFileQueueConfig forwardQueueConfig;

        private Builder() {
        }

        public Builder enabled() {
            this.enabled = true;
            return this;
        }

        public Builder disabled() {
            this.enabled = false;
            return this;
        }

        public Builder withEnabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder instant() {
            this.instant = true;
            return this;
        }

        public Builder withInstant(final boolean instant) {
            this.instant = instant;
            return this;
        }

        public Builder withName(final String name) {
            this.name = name;
            return this;
        }

        public Builder withPath(final String path) {
            this.path = path;
            return this;
        }

        public Builder withSubPathTemplate(final PathTemplateConfig subPathTemplate) {
            this.subPathTemplate = subPathTemplate;
            return this;
        }

        public Builder withForwardQueueConfig(final ForwardFileQueueConfig forwardQueueConfig) {
            this.forwardQueueConfig = forwardQueueConfig;
            return this;
        }

        public Builder withLivenessCheckPath(final String livenessCheckPath) {
            this.livenessCheckPath = livenessCheckPath;
            return this;
        }

        public Builder withLivenessCheckMode(final LivenessCheckMode livenessCheckMode) {
            this.livenessCheckMode = livenessCheckMode;
            return this;
        }

        public Builder withAtomicMoveEnabled(final boolean atomicMoveEnabled) {
            this.atomicMoveEnabled = atomicMoveEnabled;
            return this;
        }

        public ForwardFileConfig build() {
            return new ForwardFileConfig(this);
        }
    }


    // --------------------------------------------------------------------------------


    public enum LivenessCheckMode {
        /**
         * Check the path exists, ie. can be read.
         */
        READ,
        /**
         * Check that a file can be written/update in the directory
         */
        WRITE,
        ;
    }
}
