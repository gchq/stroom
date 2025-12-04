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

package stroom.pipeline.structure.client.presenter;

import stroom.pipeline.shared.data.PipelineElementType;

public class StructureValidationUtil {

    private StructureValidationUtil() {
    }

    public static boolean isValidChildType(final PipelineElementType parentType,
                                           final PipelineElementType childType,
                                           final int currentChildCount) {
        if (parentType.hasRole(PipelineElementType.ROLE_WRITER)) {
            if (currentChildCount > 0) {
                return false;
            }
            return childType.hasRole(PipelineElementType.ROLE_DESTINATION);
        }

        if (parentType.hasRole(PipelineElementType.ROLE_DESTINATION)) {
            return false;
        }

        if (parentType.hasRole(PipelineElementType.ROLE_SOURCE)) {
            return childType.hasRole(PipelineElementType.ROLE_DESTINATION)
                    || childType.hasRole(PipelineElementType.ROLE_READER)
                    || childType.hasRole(PipelineElementType.ROLE_PARSER);
        }

        if (parentType.hasRole(PipelineElementType.ROLE_READER)) {
            return childType.hasRole(PipelineElementType.ROLE_DESTINATION)
                    || childType.hasRole(PipelineElementType.ROLE_READER)
                    || childType.hasRole(PipelineElementType.ROLE_PARSER);
        }

        if (parentType.hasRole(PipelineElementType.ROLE_PARSER)) {
            return !childType.hasRole(PipelineElementType.ROLE_READER)
                    && !childType.hasRole(PipelineElementType.ROLE_PARSER)
                    && !childType.hasRole(PipelineElementType.ROLE_DESTINATION);
        }

        if (parentType.hasRole(PipelineElementType.ROLE_TARGET)) {
            if (!parentType.hasRole(PipelineElementType.ROLE_WRITER)) {
                return !childType.hasRole(PipelineElementType.ROLE_READER)
                        && !childType.hasRole(PipelineElementType.ROLE_PARSER)
                        && !childType.hasRole(PipelineElementType.ROLE_DESTINATION);
            }

            return !childType.hasRole(PipelineElementType.ROLE_READER)
                    && !childType.hasRole(PipelineElementType.ROLE_PARSER);
        }


        return !childType.hasRole(PipelineElementType.ROLE_DESTINATION);
    }
}
