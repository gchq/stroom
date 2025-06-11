package stroom.query.language;

import stroom.query.api.TableSettings;
import stroom.query.api.token.KeywordGroup;

public interface VisualisationTokenConsumer {

    TableSettings processVis(KeywordGroup keywordGroup,
                             TableSettings parentTableSettings);
}
