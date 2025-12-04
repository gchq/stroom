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

package stroom.util.client;

import stroom.explorer.shared.StringMatchLocation;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextRangeUtil {

    @Test
    void test() {
        final String data = """
                {
                  "elements" : {
                    "add" : [ {
                      "id" : "combinedParser",
                      "type" : "CombinedParser"
                    }, {
                      "id" : "splitFilter",
                      "type" : "SplitFilter"
                    }, {
                      "id" : "idEnrichmentFilter",
                      "type" : "IdEnrichmentFilter"
                    }, {
                      "id" : "xsltFilter",
                      "type" : "XSLTFilter"
                    }, {
                      "id" : "schemaFilter",
                      "type" : "SchemaFilter"
                    }, {
                      "id" : "searchResultOutputFilter",
                      "type" : "DynamicSearchResultOutputFilter"
                    } ]
                  },
                  "properties" : {
                    "add" : [ {
                      "element" : "splitFilter",
                      "name" : "splitDepth",
                      "value" : {
                        "integer" : 1
                      }
                    }, {
                      "element" : "splitFilter",
                      "name" : "splitCount",
                      "value" : {
                        "integer" : 100
                      }
                    }, {
                      "element" : "schemaFilter",
                      "name" : "schemaGroup",
                      "value" : {
                        "string" : "INDEX_DOCUMENTS"
                      }
                    } ]
                  },
                  "links" : {
                    "add" : [ {
                      "from" : "combinedParser",
                      "to" : "splitFilter"
                    }, {
                      "from" : "splitFilter",
                      "to" : "idEnrichmentFilter"
                    }, {
                      "from" : "idEnrichmentFilter",
                      "to" : "xsltFilter"
                    }, {
                      "from" : "xsltFilter",
                      "to" : "schemaFilter"
                    }, {
                      "from" : "schemaFilter",
                      "to" : "searchResultOutputFilter"
                    } ]
                  }
                }""";

        final List<StringMatchLocation> locations = new ArrayList<>();
        locations.add(new StringMatchLocation(725, 4));
        locations.add(new StringMatchLocation(934, 4));
        locations.add(new StringMatchLocation(1143, 4));

        final TextRange[] textRanges = TextRangeUtil.convertMatchesToRanges(data, locations).toArray(new TextRange[0]);

        assertThat(textRanges.length).isEqualTo(locations.size());
        assertThat(textRanges[0].getFrom().getLineNo()).isEqualTo(textRanges[0].getTo().getLineNo());
        assertThat(textRanges[0].getFrom().getColNo()).isEqualTo(4);
        assertThat(textRanges[0].getTo().getColNo()).isEqualTo(7);

        assertThat(textRanges[1].getFrom().getLineNo()).isEqualTo(textRanges[1].getTo().getLineNo());
        assertThat(textRanges[1].getFrom().getColNo()).isEqualTo(2);
        assertThat(textRanges[1].getTo().getColNo()).isEqualTo(5);

        assertThat(textRanges[2].getFrom().getLineNo()).isEqualTo(54);
        assertThat(textRanges[2].getTo().getLineNo()).isEqualTo(55);
        assertThat(textRanges[2].getFrom().getColNo()).isEqualTo(6);
        assertThat(textRanges[2].getTo().getColNo()).isEqualTo(0);
    }
}
