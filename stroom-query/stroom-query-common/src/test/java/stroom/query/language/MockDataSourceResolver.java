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

package stroom.query.language;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;

import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

public class MockDataSourceResolver extends DataSourceResolver {

    public static final MockDataSourceResolver INSTANCE = new MockDataSourceResolver();
    public static final DocFinder DOC_FINDER;

    static {
        DOC_FINDER = Mockito.mock(DocFinder.class);
        Mockito
                .when(DOC_FINDER.findByName(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyBoolean()))
                .thenAnswer(i -> {
                    final String name = i.getArgument(1, String.class);
                    if (name.equals("my_dictionary")) {
                        return List.of(DocRef.builder()
                                .type("Dictionary")
                                .uuid("my_dictionary")
                                .name("my_dictionary")
                                .build());
                    }
                    return Collections.emptyList();
                });
    }

    public static MockDataSourceResolver getInstance() {
        return INSTANCE;
    }

    private MockDataSourceResolver() {
        super(() -> DOC_FINDER, null);
    }

    @Override
    public DocRef resolveDataSourceRef(final String name) {
        return new DocRef(name, name, name);
    }
}
