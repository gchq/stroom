package stroom.core.sysinfo;

import stroom.util.sysinfo.SystemInfoResult;

import java.util.List;
import java.util.Set;

public interface SystemInfoService {

    List<SystemInfoResult> getAll();

    Set<String> getNames();

    SystemInfoResult get(final String name);
}
