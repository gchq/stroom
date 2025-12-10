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

package stroom.pipeline.legacy;

import stroom.util.shared.CompareBuilder;
import stroom.util.shared.Copyable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Objects;

/**
 * <p>
 * Java class for PipelineReference complex type.
 * <p>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p>
 * <pre>
 * &lt;complexType name="PipelineReference">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="element" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="pipeline" type="{http://www.example.org/pipeline-data}EntityReference"/>
 *         &lt;element name="feed" type="{http://www.example.org/pipeline-data}EntityReference"/>
 *         &lt;element name="streamType" type="{http://www.example.org/pipeline-data}EntityReference"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PipelineReference", propOrder = {"element", "name", "pipeline", "feed", "streamType"})
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"element", "name", "pipeline", "feed", "streamType"})
public final class PipelineReference implements Comparable<PipelineReference>, Copyable<PipelineReference> {

    @XmlElement(name = "element", required = true)
    @JsonProperty
    private String element;
    @XmlElement(name = "name", required = true)
    @JsonProperty
    private String name;
    @XmlElement(name = "pipeline", required = true)
    @JsonProperty
    private DocRef pipeline;
    @XmlElement(name = "feed", required = true)
    @JsonProperty
    private DocRef feed;
    @XmlElement(name = "streamType", required = true)
    @JsonProperty
    private String streamType;

    @XmlTransient
    @JsonIgnore
    private int hashCode = -1;

    public PipelineReference() {
    }

    public PipelineReference(final DocRef pipeline, final DocRef feed,
                             final String streamType) {
        this(null, null, pipeline, feed, streamType);
    }

    @JsonCreator
    public PipelineReference(@JsonProperty("element") final String element,
                             @JsonProperty("name") final String name,
                             @JsonProperty("pipeline") final DocRef pipeline,
                             @JsonProperty("feed") final DocRef feed,
                             @JsonProperty("streamType") final String streamType) {
        this.element = element;
        this.name = name;
        this.pipeline = pipeline;
        this.feed = feed;
        this.streamType = streamType;
    }

    public String getElement() {
        return element;
    }

    public void setElement(final String value) {
        this.element = value;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        this.name = value;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef value) {
        this.pipeline = value;
    }

    public DocRef getFeed() {
        return feed;
    }

    public void setFeed(final DocRef value) {
        this.feed = value;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(final String value) {
        this.streamType = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineReference that = (PipelineReference) o;
        return Objects.equals(element, that.element) &&
               Objects.equals(name, that.name) &&
               Objects.equals(pipeline, that.pipeline) &&
               Objects.equals(feed, that.feed) &&
               Objects.equals(streamType, that.streamType);
    }

    @Override
    public int hashCode() {
        if (hashCode == -1) {
            hashCode = Objects.hash(element, name, pipeline, feed, streamType);
        }
        return hashCode;
    }

    @Override
    public int compareTo(final PipelineReference o) {
        final CompareBuilder builder = new CompareBuilder();
        builder.append(element, o.element);
        builder.append(name, o.name);
        builder.append(pipeline, o.pipeline);
        builder.append(feed, o.feed);
        builder.append(streamType, o.streamType);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        return "element=" + element + ", name=" + name + ", pipeline=" + pipeline + ", feed=" + feed + ", streamType="
               + streamType;
    }

    @Override
    public void copyFrom(final PipelineReference other) {
        this.element = other.element;
        this.name = other.name;
        this.pipeline = other.pipeline;
        this.feed = other.feed;
        this.streamType = other.streamType;
    }
}
