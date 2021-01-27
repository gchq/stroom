package stroom.query.api.current;

import org.junit.jupiter.api.Test;
import stroom.query.api.v2.SearchRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * If a model is part of an API it should often not be changed without re-versioning, - otherwise anything that depends
 * on it could fail. But it is easy, in the heat of a big change, to forget one has changed a model. This test is meant
 * to catch these cases and alert the developer.
 * <p>
 * After being alerted of the model change a developer will probably want to follow some model-change process. After doing
 * that the test that fails here could be resolved by temporarily uncommenting the line in the test that writes out the
 * model's portrait. Don't commit this test with the line uncommented! If you do the test will always pass.
 */

class ModelChangeDetector {

    private static final String SEARCH_REQUEST_PORTRAIT_FILE_CURRENT = "src/test/resources/searchRequestPortrait-current.txt";
    private static final String SEARCH_REQUEST_PORTRAIT_FILE_NEW = "src/test/resources/searchRequestPortrait-new.txt";

    @Test
    void detect_changes_in_SearchRequest() throws IOException {
        String newPortrait = ClassPhotographer.takePortraitOf(SearchRequest.class, "stroom.query.api");

        // Uncomment this line to update the model portrait
//        Files.write(Paths.get(SEARCH_REQUEST_PORTRAIT_FILE_CURRENT), newPortrait.getBytes());


        //write the new portrait to a file ignored by git so you can diff the old and new if the
        //assertion fails
        Files.write(Paths.get(SEARCH_REQUEST_PORTRAIT_FILE_NEW), newPortrait.getBytes());

        String currentPortrait = new String(Files.readAllBytes(Paths.get(SEARCH_REQUEST_PORTRAIT_FILE_CURRENT)));

        System.out.println("If the test fails compare the contents of the following two files to establish how the api has changed");
        System.out.println(SEARCH_REQUEST_PORTRAIT_FILE_CURRENT);
        System.out.println(SEARCH_REQUEST_PORTRAIT_FILE_NEW);
        System.out.println("");
        System.out.println("If the change is a breaking change you will need to uplift the major version number (see uplift*.sh) on release");
        System.out.println("If the change is a non-breaking change you will just need to uplift the minor or patch version on release");

        System.out.println("CURRENT\n" + currentPortrait);
        System.out.println("NEW\n" + newPortrait);

        assertThat(newPortrait).isEqualTo(currentPortrait);
    }
}
