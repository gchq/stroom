package stroom.app.sysinfo;

import stroom.util.sysinfo.SystemInfoResult;

import java.util.List;

public interface SystemInfoService {

    List<SystemInfoResult> getAll();

    SystemInfoResult get(final String name);
}
