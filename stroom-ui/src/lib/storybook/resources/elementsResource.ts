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
    .get(`${testConfig.stroomBaseServiceUrl}/elements/v1/elements`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.elements);
    });
  server
    .get(`${testConfig.stroomBaseServiceUrl}/elements/v1/elementProperties`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.elementProperties);
    });
};

export default resourceBuilder;
