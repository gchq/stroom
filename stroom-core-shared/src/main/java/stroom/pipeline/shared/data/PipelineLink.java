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

package stroom.pipeline.shared.data;

import stroom.docref.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for Link complex type.
 * <p>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p>
 * <pre>
 * &lt;complexType name="Link">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="from" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="to" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Link", propOrder = {"from", "to"})
public class PipelineLink implements Comparable<PipelineLink>, SharedObject {
    private static final long serialVersionUID = 8520066243443177869L;

    @XmlTransient
    private SourcePipeline source;

    @XmlElement(required = true)
    private String from;
    @XmlElement(required = true)
    private String to;

    public PipelineLink() {
        // Default constructor necessary for GWT serialisation.
    }

    public PipelineLink(final String from, final String to) {
        this.from = from;
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(final String value) {
        this.from = value;
    }

    public String getTo() {
        return to;
    }

    public void setTo(final String value) {
        this.to = value;
    }

    public SourcePipeline getSource() {
        return source;
    }

    public void setSource(final SourcePipeline source) {
        this.source = source;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (!(o instanceof PipelineLink)) {
            return false;
        }

        final PipelineLink link = (PipelineLink) o;

        if (from != null && !from.equals(link.from)) {
            return false;
        }

        if (link.from != null && !link.from.equals(from)) {
            return false;
        }

        if (to != null && !to.equals(link.to)) {
            return false;
        }

        return !(link.to != null && !link.to.equals(to));

    }

    @Override
    public int hashCode() {
        int code = 31;
        if (from == null) {
            code = code * 31;
        } else {
            code = code * 31 + from.hashCode();
        }
        if (to == null) {
            code = code * 31;
        } else {
            code = code * 31 + to.hashCode();
        }
        return code;
    }

    @Override
    public int compareTo(final PipelineLink o) {
        if (!(from.equals(o.from))) {
            return from.compareTo(o.from);
        }

        if (!(to.equals(o.to))) {
            return to.compareTo(o.to);
        }

        return 0;
    }

    @Override
    public String toString() {
        return "from=" + from + ", to=" + to;
    }
}
