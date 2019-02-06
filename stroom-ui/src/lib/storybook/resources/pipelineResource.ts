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
    .get(`${testConfig.stroomBaseServiceUrl}/pipelines/v1/:pipelineId`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const pipeline = testCache.data!.pipelines[req.params.pipelineId];
      if (pipeline) {
        res.json(pipeline);
      } else {
        res.sendStatus(404);
      }
    });
  server
    .get(`${testConfig.stroomBaseServiceUrl}/pipelines/v1/`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json({
        total: Object.keys(testCache.data!.pipelines).length,
        pipelines: Object.keys(testCache.data!.pipelines).map(p => ({
          uuid: p,
          name: p,
          type: "Pipeline"
        }))
      });
    });
  server
    .post(`${testConfig.stroomBaseServiceUrl}/pipelines/v1/:pipelineId`)
    .intercept((req: HttpRequest, res: HttpResponse) => res.sendStatus(200));
};

export default resourceBuilder;
