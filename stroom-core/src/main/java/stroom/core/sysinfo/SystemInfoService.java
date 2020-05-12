package stroom.core.sysinfo;

import stroom.util.sysinfo.SystemInfoResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SystemInfoService {

    List<SystemInfoResult> getAll();

    Set<String> getNames();

    Optional<SystemInfoResult> get(final String name);
}
