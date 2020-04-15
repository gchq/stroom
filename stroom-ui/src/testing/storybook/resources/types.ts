import { Config } from "startup/config/types";
import { TestCache } from "../PollyDecorator";

export type ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
  testCache: TestCache,
) => any;
