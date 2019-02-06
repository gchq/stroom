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
    .get(`${testConfig.stroomBaseServiceUrl}/dictionary/v1/:dictionaryUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const dict = testCache.data!.dictionaries[req.params.dictionaryUuid];
      if (dict) {
        res.json(dict);
      } else {
        res.sendStatus(404);
      }
    });
  server
    .post(`${testConfig.stroomBaseServiceUrl}/dictionary/v1/:dictionaryUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => res.sendStatus(200));
};

export default resourceBuilder;
