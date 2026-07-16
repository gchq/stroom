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
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "title", schema = ChangeTitle.class),
                @DiscriminatorMapping(value = "subject", schema = ChangeSubject.class),
                @DiscriminatorMapping(value = "addTag", schema = AddTag.class),
                @DiscriminatorMapping(value = "removeTag", schema = RemoveTag.class),
                @DiscriminatorMapping(value = "setTag", schema = SetTag.class),
                @DiscriminatorMapping(value = "assignedTo", schema = ChangeAssignedTo.class),
                @DiscriminatorMapping(value = "comment", schema = ChangeComment.class),
                @DiscriminatorMapping(value = "retentionPeriod", schema = ChangeRetentionPeriod.class),
                @DiscriminatorMapping(value = "description", schema = ChangeDescription.class),
                @DiscriminatorMapping(value = "linkEvents", schema = LinkEvents.class),
                @DiscriminatorMapping(value = "unlinkEvents", schema = UnlinkEvents.class),
                @DiscriminatorMapping(value = "linkAnnotations", schema = LinkAnnotations.class),
                @DiscriminatorMapping(value = "unlinkAnnotations", schema = UnlinkAnnotations.class),
                @DiscriminatorMapping(value = "addTable", schema = AddAnnotationTable.class)})
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


    // --------------------------------------------------------------------------------


    public sealed interface HasAnnotationTag permits AddTag, RemoveTag, SetTag {

        AnnotationTag getTag();
    }
}
