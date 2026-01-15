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

package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChangeTitle.class, name = "title"),
        @JsonSubTypes.Type(value = ChangeSubject.class, name = "subject"),
        @JsonSubTypes.Type(value = AddTag.class, name = "addTag"),
        @JsonSubTypes.Type(value = RemoveTag.class, name = "removeTag"),
        @JsonSubTypes.Type(value = SetTag.class, name = "setTag"),
        @JsonSubTypes.Type(value = ChangeAssignedTo.class, name = "assignedTo"),
        @JsonSubTypes.Type(value = ChangeComment.class, name = "comment"),
        @JsonSubTypes.Type(value = ChangeRetentionPeriod.class, name = "retentionPeriod"),
        @JsonSubTypes.Type(value = ChangeDescription.class, name = "description"),
        @JsonSubTypes.Type(value = LinkEvents.class, name = "linkEvents"),
        @JsonSubTypes.Type(value = UnlinkEvents.class, name = "unlinkEvents"),
        @JsonSubTypes.Type(value = LinkAnnotations.class, name = "linkAnnotations"),
        @JsonSubTypes.Type(value = UnlinkAnnotations.class, name = "unlinkAnnotations"),
        @JsonSubTypes.Type(value = AddAnnotationTable.class, name = "addTable"),
})
public abstract sealed class AbstractAnnotationChange permits
        ChangeTitle,
        ChangeSubject,
        AddTag,
        RemoveTag,
        SetTag,
        ChangeAssignedTo,
        ChangeComment,
        ChangeRetentionPeriod,
        ChangeDescription,
        LinkEvents,
        UnlinkEvents,
        LinkAnnotations,
        UnlinkAnnotations,
        AddAnnotationTable {

}
