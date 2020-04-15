import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
  testCache: TestCache,
) => {
  const resource = `${stroomBaseServiceUrl}/streamtasks/v1/`;

  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) =>
    res.json({
      streamTasks: testCache.data!.trackers || [],
      totalStreamTasks: testCache.data!.trackers
        ? testCache.data!.trackers.length
        : 0,
    }),
  );
};

export default resourceBuilder;
