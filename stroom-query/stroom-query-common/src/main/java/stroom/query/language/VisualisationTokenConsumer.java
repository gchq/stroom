package stroom.query.language;

import stroom.query.api.v2.TableSettings;
import stroom.query.language.token.KeywordGroup;

public interface VisualisationTokenConsumer {

    TableSettings processVis(KeywordGroup keywordGroup,
                             TableSettings parentTableSettings);
}
