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

import stroom.util.client.ImageUtil;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Image;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;

public class PipelineImageUtil {
    public static Image getImage(final PipelineElementType pipelineElementType) {
        return new Image(getPipelineImageURL() + pipelineElementType.getIcon());
    }

    public static String getImageURL(final PipelineElement element) {
        return getPipelineImageURL() + element.getElementType().getIcon();
    }

    private static String getPipelineImageURL() {
        return ImageUtil.getImageURL() + "pipeline/";
    }
}
