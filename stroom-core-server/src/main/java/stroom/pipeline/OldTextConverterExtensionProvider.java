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

package stroom.pipeline;

import stroom.entity.shared.BaseEntity;
import stroom.pipeline.shared.ExtensionProvider;

public class OldTextConverterExtensionProvider extends ExtensionProvider {
    public OldTextConverterExtensionProvider() {
        super("txt");
    }

    @Override
    public String getExtension(final BaseEntity entity, final String propertyName) {
        if (entity != null && propertyName != null && entity instanceof OldTextConverter && propertyName.equals("data")) {
            final OldTextConverter textConverter = (OldTextConverter) entity;
            if (textConverter.getConverterType() != null) {
                return textConverter.getConverterType().getFileExtension();
            }
        }

        return super.getExtension(entity, propertyName);
    }
}
