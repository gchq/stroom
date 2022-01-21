package stroom.proxy.repo;

import stroom.util.shared.HasPropertyPath;

import java.util.List;

public interface RepoDbConfig extends HasPropertyPath {

    String getDbDir();

    List<String> getGlobalPragma();

    List<String> getConnectionPragma();
}
