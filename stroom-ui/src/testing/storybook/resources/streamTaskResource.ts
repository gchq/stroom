import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => {
  const resource = apiUrl("/streamtasks/v1");

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
