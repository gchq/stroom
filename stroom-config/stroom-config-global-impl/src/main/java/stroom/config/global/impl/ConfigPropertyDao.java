package stroom.config.global.impl;

import stroom.config.global.api.ConfigProperty;
import stroom.util.shared.HasIntCrud;

import java.util.List;

public interface ConfigPropertyDao extends HasIntCrud<ConfigProperty> {
    boolean delete(String name);

    List<ConfigProperty> list();
}
