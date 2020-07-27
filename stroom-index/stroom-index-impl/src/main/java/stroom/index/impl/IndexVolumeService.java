package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

public interface IndexVolumeService extends Clearable {
    ResultPage<IndexVolume> find(ExpressionCriteria criteria);

    IndexVolume create(IndexVolume indexVolume);

    IndexVolume read(int id);

    IndexVolume update(IndexVolume indexVolume);

    Boolean delete(int id);

    void rescan();
}