import { Config } from "../../../startup/config";
import { TestCache } from "../PollyDecorator";

export type ResourceBuilder = (
  server: any,
  testConfig: Config,
  testCache: TestCache
) => any;
