package stroom.app.docs;

import stroom.test.common.docs.StroomDocsUtil.GeneratesDocumentation;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

public class GenerateAllDocumentation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateAllDocumentation.class);

    /**
     * Run this to generate all stroom-docs content and update it in the relevant
     * stroom-docs files. Assumes stroom-docs is in a dir named 'stroom-docs' that is also
     * a sibling of this stroom repo.
     * If not set system prop
     * {@link stroom.test.common.docs.StroomDocsUtil#STROOM_DOCS_REPO_DIR_PROP_KEY}
     */
    @GeneratesDocumentation
    public static void main(String[] args) {
        LOGGER.info("Generating documentation for pipeline elements");
        GeneratePipelineElementsDoc.main(args);

        LOGGER.info("Generating documentation for snippets");
        GenerateSnippetsDoc.main(args);

        LOGGER.info("Done");
    }
}
