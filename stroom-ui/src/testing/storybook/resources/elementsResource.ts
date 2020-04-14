import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
  testCache: TestCache,
) => {
  const resource = `${stroomBaseServiceUrl}/elements/v1/`;

  server
    .get(`${resource}/elements`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.elements);
    });
  server
    .get(`${resource}/elementProperties`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.elementProperties);
    });
};

export default resourceBuilder;
