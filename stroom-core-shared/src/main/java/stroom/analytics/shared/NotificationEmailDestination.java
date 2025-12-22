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

package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class NotificationEmailDestination extends NotificationDestination {

    @JsonProperty
    private final String to;
    @JsonProperty
    private final String cc;
    @JsonProperty
    private final String bcc;
    @JsonProperty
    private final String subjectTemplate;
    @JsonProperty
    private final String bodyTemplate;

    @JsonCreator
    public NotificationEmailDestination(
            @JsonProperty("to") final String to,
            @JsonProperty("cc") final String cc,
            @JsonProperty("bcc") final String bcc,
            @JsonProperty("subjectTemplate") final String subjectTemplate,
            @JsonProperty("bodyTemplate") final String bodyTemplate) {

        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
    }

    public String getTo() {
        return to;
    }

    public String getCc() {
        return cc;
    }

    public String getBcc() {
        return bcc;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NotificationEmailDestination that = (NotificationEmailDestination) o;
        return Objects.equals(to, that.to) &&
               Objects.equals(cc, that.cc) &&
               Objects.equals(bcc, that.bcc) &&
               Objects.equals(subjectTemplate, that.subjectTemplate) &&
               Objects.equals(bodyTemplate, that.bodyTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(to, cc, bcc, subjectTemplate, bodyTemplate);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private String to;
        private String cc;
        private String bcc;
        private String subjectTemplate;
        private String bodyTemplate;

        private Builder() {
        }

        private Builder(final NotificationEmailDestination config) {
            this.to = config.to;
            this.cc = config.cc;
            this.bcc = config.bcc;
            this.subjectTemplate = config.subjectTemplate;
            this.bodyTemplate = config.bodyTemplate;
        }

        public Builder to(final String to) {
            this.to = to;
            return this;
        }

        public Builder cc(final String cc) {
            this.cc = cc;
            return this;
        }

        public Builder bcc(final String bcc) {
            this.bcc = bcc;
            return this;
        }

        public Builder subjectTemplate(final String subjectTemplate) {
            this.subjectTemplate = subjectTemplate;
            return this;
        }

        public Builder bodyTemplate(final String bodyTemplate) {
            this.bodyTemplate = bodyTemplate;
            return this;
        }

        public NotificationEmailDestination build() {
            return new NotificationEmailDestination(
                    to,
                    cc,
                    bcc,
                    subjectTemplate,
                    bodyTemplate);
        }
    }
}
