import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";
import { BuildInfo } from "components/BuildInfo/api/types";

const BUILD_INFO: BuildInfo = {
  userName: "testuser",
  buildVersion: "1.2.3",
  buildDate: "2019-01-01T00:00:00.000Z",
  upDate: "2019-01-02T00:00:00.000Z",
  nodeName: "testnode",
};

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
) => {
  const resource = `${stroomBaseServiceUrl}/build-info/v1/`;

  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(BUILD_INFO);
  });
};

export default resourceBuilder;
