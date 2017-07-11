/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.structure.client.view;

import stroom.pipeline.shared.data.PipelineElementType;
import stroom.svg.client.SvgIcon;
import stroom.util.client.ImageUtil;

public class PipelineImageUtil {
    public static SvgIcon getIcon(final PipelineElementType pipelineElementType) {
        if (pipelineElementType.getIcon() == null || pipelineElementType.getIcon().length() == 0) {
            return null;
        }

        return new SvgIcon(getPipelineImageURL() + pipelineElementType.getIcon(), 16, 16);
    }

    private static String getPipelineImageURL() {
        return ImageUtil.getImageURL() + "pipeline/";
    }
}
