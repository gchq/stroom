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

package stroom.streamstore.shared;

import stroom.docref.SharedObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamAttributeMap implements SharedObject {
    private static final long serialVersionUID = -8198186456924478908L;

    private Stream stream;
    private List<String> fileNameList;
    private Map<StreamAttributeKey, String> attributeMap = new HashMap<>();
    private Map<String, String> nameValueMap = new HashMap<>();
    private Map<String, StreamAttributeKey> nameKeyMap = new HashMap<>();

    public StreamAttributeMap() {
        // Default constructor necessary for GWT serialisation.
    }

    public StreamAttributeMap(Stream stream) {
        setStream(stream);
    }

    public Stream getStream() {
        return stream;
    }

    public void setStream(Stream stream) {
        this.stream = stream;
    }

    public void addAttribute(StreamAttributeKey key, String value) {
        attributeMap.put(key, value);
        nameValueMap.put(key.getName(), value);
        nameKeyMap.put(key.getName(), key);
    }

    public void addAttribute(String name, String value) {
        nameValueMap.put(name, value);
    }

    public String getAttributeValue(final String name) {
        return nameValueMap.get(name);
    }

    public String getAttributeValue(final StreamAttributeKey key) {
        return attributeMap.get(key);
    }

    public Set<String> getAttributeKeySet() {
        return nameValueMap.keySet();
    }

    public Map<String, String> asMap(){
        return nameValueMap;
    }

    public String formatAttribute(String name) {
        StreamAttributeKey streamAttributeKey = nameKeyMap.get(name);
        if (streamAttributeKey == null) {
            String value = nameValueMap.get(name);
            if (value != null) {
                return value;
            } else {
                return "";
            }
        }
        return streamAttributeKey.format(this, streamAttributeKey);
    }

    public List<String> getFileNameList() {
        return fileNameList;
    }

    public void setFileNameList(List<String> fileNameList) {
        this.fileNameList = fileNameList;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamAttributeMap)) return false;

        final StreamAttributeMap that = (StreamAttributeMap) o;

        return stream.equals(that.stream);
    }

    @Override
    public int hashCode() {
        return stream.hashCode();
    }
}
