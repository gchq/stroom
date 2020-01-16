package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.util.shared.HasIntCrud;

import java.util.List;
import java.util.Optional;

public interface ConfigPropertyDao extends HasIntCrud<ConfigProperty> {

    boolean delete(String name);

    boolean delete(String name);

    List<ConfigProperty> list();

    Optional<ConfigProperty> fetch(final String propertyName);

}
