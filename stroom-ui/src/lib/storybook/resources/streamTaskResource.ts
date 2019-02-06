import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "../../../startup/config";
import { ResourceBuilder } from "./resourceBuilder";

const resourceBuilder: ResourceBuilder = (
  server: any,
  testConfig: Config,
  testCache: TestCache
) => {
  server
    .get(`${testConfig.stroomBaseServiceUrl}/streamTasks/v1/`)
    .intercept((req: HttpRequest, res: HttpResponse) =>
      res.json({
        streamTasks: testCache.data!.trackers || [],
        totalStreamTasks: testCache.data!.trackers
          ? testCache.data!.trackers.length
          : 0
      })
    );
};

export default resourceBuilder;
