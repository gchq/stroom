import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => {
  const resource = apiUrl("/pipelines/v1");

  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json({
      total: testCache.data!.documents.Pipeline.length,
      pipelines: testCache.data!.documents.Pipeline,
    });
  });
};

export default resourceBuilder;
