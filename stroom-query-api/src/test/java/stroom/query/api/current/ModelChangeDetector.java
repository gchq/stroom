package stroom.query.api.current;

import org.junit.Test;
import stroom.query.api.SearchRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * If a model is part of an API it should often not be changed without re-versioning, - otherwise anything that depends
 * on it could fail. But it is easy, in the heat of a big change, to forget one has changed a model. This test is meant
 * to catch these cases and alert the developer.
 *
 * After being alerted of the model change a developer will probably want to follow some model-change process. After doing
 * that the test that fails here could be resolved by temporarily uncommenting the line in the test that writes out the
 * model's portrait. Don't commit this test with the line uncommented! If you do the test will always pass.
 */
public class ModelChangeDetector {

    private static final String SEARCH_REQUEST_PORTRAIT_FILE = "src/test/resources/searchRequestPortrait.txt";

    @Test
    public void detect_changes_in_SearchRequest() throws IOException {
        String newPortrait = ClassPhotographer.takePortraitOf(SearchRequest.class, "stroom.query.api");
        // Uncomment this line to update the model portrait
//        Files.write(Paths.get(SEARCH_REQUEST_PORTRAIT_FILE), newPortrait.getBytes());

        String existingPortrait = new String(Files.readAllBytes(Paths.get(SEARCH_REQUEST_PORTRAIT_FILE)));
        assertThat(existingPortrait, equalTo(newPortrait));
    }
}
