/*
 * Copyright 2017 Crown Copyright
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

package stroom.db.migration._V07_00_00.doc.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.db.migration._V07_00_00.docref._V07_00_00_SharedObject;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_Copyable;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_CompareBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
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
public final class _V07_00_00_PipelineReference
        implements Comparable<_V07_00_00_PipelineReference>, _V07_00_00_SharedObject, _V07_00_00_Copyable<_V07_00_00_PipelineReference> {
    private static final long serialVersionUID = -8037614920682819123L;
    @XmlElement(name = "element", required = true)
    protected String element;
    @XmlElement(name = "name", required = true)
    protected String name;
    @XmlElement(name = "pipeline", required = true)
    protected _V07_00_00_DocRef pipeline;
    @XmlElement(name = "feed", required = true)
    protected _V07_00_00_DocRef feed;
    @XmlElement(name = "streamType", required = true)
    protected String streamType;

    @XmlTransient
    private _V07_00_00_SourcePipeline source;

    @XmlTransient
    @JsonIgnore
    private int hashCode = -1;

    public _V07_00_00_PipelineReference() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_PipelineReference(final _V07_00_00_DocRef pipeline, final _V07_00_00_DocRef feed,
                                        final String streamType) {
        this(null, null, pipeline, feed, streamType);
    }

    public _V07_00_00_PipelineReference(final String element, final String name, final _V07_00_00_DocRef pipeline,
                                        final _V07_00_00_DocRef feed, final String streamType) {
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

    public _V07_00_00_DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final _V07_00_00_DocRef value) {
        this.pipeline = value;
    }

    public _V07_00_00_DocRef getFeed() {
        return feed;
    }

    public void setFeed(final _V07_00_00_DocRef value) {
        this.feed = value;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(final String value) {
        this.streamType = value;
    }

    public _V07_00_00_SourcePipeline getSource() {
        return source;
    }

    public void setSource(final _V07_00_00_SourcePipeline source) {
        this.source = source;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final _V07_00_00_PipelineReference that = (_V07_00_00_PipelineReference) o;
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
    public int compareTo(final _V07_00_00_PipelineReference o) {
        final _V07_00_00_CompareBuilder builder = new _V07_00_00_CompareBuilder();
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
    public void copyFrom(final _V07_00_00_PipelineReference other) {
        this.source = other.source;
        this.element = other.element;
        this.name = other.name;
        this.pipeline = other.pipeline;
        this.feed = other.feed;
        this.streamType = other.streamType;
    }
}
