import { TestCache } from "../PollyDecorator";

export type ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => any;
