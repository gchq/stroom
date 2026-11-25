/*
 * Copyright 2026 Crown Copyright
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

package stroom.test.common.docs;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomDocsUtil {

    // Most XSLT functions are package-private, so a scan of public classes alone would leave them out of the docs.
    @Test
    void doWithClassScanResult_includesClassesOfAnyVisibility() {
        final List<String> classNames = new ArrayList<>();

        StroomDocsUtil.doWithClassScanResult(scanResult ->
                classNames.addAll(scanResult.getClassesWithAnnotation(ScanMarker.class).getNames()));

        assertThat(classNames)
                .containsExactlyInAnyOrder(
                        PublicClass.class.getName(),
                        PackagePrivateClass.class.getName(),
                        PrivateClass.class.getName());
    }


    // --------------------------------------------------------------------------------


    @Retention(RetentionPolicy.RUNTIME)
    @interface ScanMarker {

    }


    // --------------------------------------------------------------------------------


    @ScanMarker
    public static class PublicClass {

    }


    // --------------------------------------------------------------------------------


    @ScanMarker
    static class PackagePrivateClass {

    }


    // --------------------------------------------------------------------------------


    @ScanMarker
    private static class PrivateClass {

    }
}
