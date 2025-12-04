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

package stroom.query.api.current;

import stroom.query.api.SearchRequest;
import stroom.test.common.ComparisonHelper;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * If a model is part of an API it should often not be changed without re-versioning, - otherwise
 * anything that depends on it could fail. But it is easy, in the heat of a big change, to forget one has
 * changed a model. This test is meant to catch these cases and alert the developer.
 * <p>
 * After being alerted of the model change a developer will probably want to follow some model-change process.
 * After doing that the test that fails here could be resolved by temporarily uncommenting the line in the
 * test that writes out the model's portrait. Don't commit this test with the line uncommented! If you do the
 * test will always pass.
 */

class ModelChangeDetector {

    private static final Path SEARCH_REQUEST_PORTRAIT_FILE_CURRENT = Paths.get(
            "src/test/resources/searchRequestPortrait-current.txt");
    private static final Path SEARCH_REQUEST_PORTRAIT_FILE_NEW = Paths.get(
            "src/test/resources/searchRequestPortrait-new.txt");

    @Test
    void detect_changes_in_SearchRequest() throws IOException {
        final String newPortrait = ClassPhotographer.takePortraitOf(SearchRequest.class, "stroom.query.api");

        // Uncomment this line to update the model portrait
//        Files.write(SEARCH_REQUEST_PORTRAIT_FILE_CURRENT, newPortrait.getBytes());

        //write the new portrait to a file ignored by git so you can diff the old and new if the
        //assertion fails
        Files.write(SEARCH_REQUEST_PORTRAIT_FILE_NEW, newPortrait.getBytes());

        final String currentPortrait = new String(Files.readAllBytes(SEARCH_REQUEST_PORTRAIT_FILE_CURRENT));

        System.out.println("Ir the test fails compare the contents of the following two files to " +
                "establish how the api has changed");
        System.out.println(SEARCH_REQUEST_PORTRAIT_FILE_CURRENT.toAbsolutePath().normalize().toString());
        System.out.println(SEARCH_REQUEST_PORTRAIT_FILE_NEW.toAbsolutePath().normalize().toString());
        System.out.println("");
        System.out.println("If the change is a breaking change you will need to uplift the major " +
                "version number (see uplift*.sh) on release");
        System.out.println("If the change is a non-breaking change you will just need to uplift the " +
                "minor or patch version on release");

        final boolean areFilesTheSame = ComparisonHelper.unifiedDiff(
                SEARCH_REQUEST_PORTRAIT_FILE_CURRENT,
                SEARCH_REQUEST_PORTRAIT_FILE_NEW);

        if (areFilesTheSame) {
            System.out.println("\nFiles are the same");
        } else {
            Assertions.fail("Files are different.");
        }
    }
}
