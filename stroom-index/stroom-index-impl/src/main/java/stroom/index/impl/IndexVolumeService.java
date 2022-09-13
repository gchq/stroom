package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.ValidationResult;
import stroom.util.shared.ResultPage;

public interface IndexVolumeService {
    ResultPage<IndexVolume> find(ExpressionCriteria criteria);

    ValidationResult validate(final IndexVolume request);

    IndexVolume create(IndexVolume indexVolume);

    IndexVolume read(int id);

    IndexVolume update(IndexVolume indexVolume);

    Boolean delete(int id);

    void rescan();

    /**
     * Uses the configured volume selector to select an index volume from group
     * groupName and belonging to node nodeName.
     * @throws stroom.index.shared.IndexException When no suitable {@link IndexVolume} can
     * be found.
     */
    IndexVolume selectVolume(final String groupName, final String nodeName);
}
