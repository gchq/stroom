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

package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class DocumentTypes {

    public static final String[] FOLDER_TYPES = new String[]{
            ExplorerConstants.SYSTEM,
            ExplorerConstants.FOLDER
    };

    public static final Set<String> FOLDER_TYPES_SET = Collections.unmodifiableSet(Arrays.stream(FOLDER_TYPES)
            .collect(Collectors.toSet()));

    @JsonProperty
    private final List<DocumentType> types;
    @JsonProperty
    private final List<DocumentType> visibleTypes;
    @JsonIgnore
    private final Map<String, DocumentType> typeToDocumentTypeMap;

    @JsonCreator
    public DocumentTypes(@JsonProperty("types") final List<DocumentType> types,
                         @JsonProperty("visibleTypes") final List<DocumentType> visibleTypes) {
        this.types = types;
        this.visibleTypes = visibleTypes;
        this.typeToDocumentTypeMap = types.stream()
                .collect(Collectors.toMap(DocumentType::getType, Function.identity()));
    }

    public List<DocumentType> getTypes() {
        return types;
    }

    public List<DocumentType> getVisibleTypes() {
        return visibleTypes;
    }

    public static boolean isFolder(final String type) {
        return FOLDER_TYPES_SET.contains(type);
    }

    public static boolean isSystem(final String type) {
        return ExplorerConstants.SYSTEM.equals(type);
    }

    public Optional<DocumentType> getDocumentType(final String type) {
        return Optional.ofNullable(typeToDocumentTypeMap.get(type));
    }
}
