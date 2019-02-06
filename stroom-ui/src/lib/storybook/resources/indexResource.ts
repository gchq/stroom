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
    .get(`${testConfig.stroomBaseServiceUrl}/index/v1/:indexUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const index = testCache.data!.indexes[req.params.indexUuid];
      if (index) {
        res.setHeader("Content-Type", "application/xml");
        res.send(index);
      } else {
        res.sendStatus(404);
      }
    });
  server
    .post(`${testConfig.stroomBaseServiceUrl}/xslt/v1/:indexUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => res.sendStatus(200));
};

export default resourceBuilder;
