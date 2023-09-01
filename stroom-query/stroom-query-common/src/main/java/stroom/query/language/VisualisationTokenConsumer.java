package stroom.query.language;

import stroom.query.api.v2.TableSettings;

public interface VisualisationTokenConsumer {

    TableSettings processVis(KeywordGroup keywordGroup,
                             TableSettings parentTableSettings);
}
