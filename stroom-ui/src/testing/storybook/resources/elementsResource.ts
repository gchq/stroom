import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => {
  const resource = apiUrl("/elements/v1");

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
