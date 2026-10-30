/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.data.store.api.FsVolumeStates;
import stroom.index.api.IndexVolumeStates;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// `volumes`: Stroom's data and index volumes, whether each group can still be written to, and how Stroom treats
/// volumes as they fill ([VolumeReport]). Read-only.
///
/// With the Manage Volumes permission (as the Volumes screen needs) each volume is given, with its path, state and
/// space. Without it, only whether each group can still be written to, so that a user whose data has stopped being
/// written can be told why without being shown the servers' paths and sizes.
class VolumesTool extends AbstractBuiltInTool {

    static final String NAME = "volumes";
    static final String KIND_ARG = "kind";
    static final String GROUP_ARG = "group";
    static final String DATA = "data";
    static final String INDEX = "index";
    static final String BOTH = "both";

    private final Provider<FsVolumeStates> fsVolumeStatesProvider;
    private final Provider<IndexVolumeStates> indexVolumeStatesProvider;
    private final SecurityContext securityContext;
    private final AiAuditLog auditLog;

    @Inject
    VolumesTool(final Provider<FsVolumeStates> fsVolumeStatesProvider,
                final Provider<IndexVolumeStates> indexVolumeStatesProvider,
                final SecurityContext securityContext,
                final AiAuditLog auditLog) {
        this.fsVolumeStatesProvider = fsVolumeStatesProvider;
        this.indexVolumeStatesProvider = indexVolumeStatesProvider;
        this.securityContext = securityContext;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.MANAGE_VOLUMES_PERMISSION)
                .note("Without Manage Volumes, only whether each volume group can be written to.")
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.ADMINISTRATION;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Stroom's data volumes (where streams are stored) and index volumes (where Lucene "
                             + "index shards are stored), and how Stroom treats them as they fill: e.g. 'are the "
                             + "volumes full?', 'why is data no longer being written?', 'what happens when a "
                             + "volume fills up?'. For each volume group: whether it can still be written to and "
                             + "which is the default; with the Manage Volumes permission, also each volume's "
                             + "path (or S3 bucket) or node, status, limit, total, used and free space, and "
                             + "whether it is full. 'behaviour' says what Stroom does with full volumes: answer "
                             + "from it, not from how other products behave. Read-only: it changes nothing.")
                .parameters(ObjectSchema.builder()
                        .addEnumProperty(KIND_ARG, List.of(DATA, INDEX, BOTH),
                                "Data volumes, index volumes, or both (the default).")
                        .addStringProperty(GROUP_ARG, "Only this volume group.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final String kind = Objects.requireNonNullElse(stringArg(request, KIND_ARG), BOTH);
        if (!List.of(DATA, INDEX, BOTH).contains(kind)) {
            return ToolResult.error("kind must be one of data, index or both.");
        }
        final String group = stringArg(request, GROUP_ARG);
        final boolean full = securityContext.hasAppPermission(AppPermission.MANAGE_VOLUMES_PERMISSION);
        final Map<String, Object> result = new LinkedHashMap<>();
        if (!full) {
            result.put("view", VolumeReport.REDUCED);
        }
        // Read as the processing user: the user's view is decided above, not by the volume services.
        securityContext.asProcessingUser(() -> {
            if (!INDEX.equals(kind)) {
                final FsVolumeStates states = fsVolumeStatesProvider.get();
                result.put("data_volume_groups", VolumeReport.dataGroups(states.volumesByGroup(),
                        states.defaultGroup().orElse(null), group, full));
            }
            if (!DATA.equals(kind)) {
                final IndexVolumeStates states = indexVolumeStatesProvider.get();
                result.put("index_volume_groups", VolumeReport.indexGroups(states.volumesByGroup(),
                        states.defaultGroup().orElse(null), group, full));
            }
        });
        final Map<String, Object> behaviour = new LinkedHashMap<>();
        if (!INDEX.equals(kind)) {
            behaviour.put("data_volumes", VolumeReport.DATA_BEHAVIOUR);
        }
        if (!DATA.equals(kind)) {
            behaviour.put("index_volumes", VolumeReport.INDEX_BEHAVIOUR);
        }
        result.put("behaviour", behaviour);
        auditLog.documentsSearched(context.session(), "volumes" + (group == null
                        ? ""
                        : " in group " + group) + " (" + kind + ")", List.of(),
                Map.of("View", full
                        ? "Full"
                        : "Reduced"),
                "Volumes read by the " + NAME + " tool");
        return ToolResult.text(JsonUtil.writeValueAsString(result, true));
    }
}
