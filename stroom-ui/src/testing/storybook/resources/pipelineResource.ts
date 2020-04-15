import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
  testCache: TestCache,
) => {
  const resource = `${stroomBaseServiceUrl}/pipelines/v1`;

  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json({
      total: testCache.data!.documents.Pipeline.length,
      pipelines: testCache.data!.documents.Pipeline,
    });
  });
};

export default resourceBuilder;
