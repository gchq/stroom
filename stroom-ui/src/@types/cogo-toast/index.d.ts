declare module "cogo-toast" {
  interface CogoToastOptions {
    hideAfter?: number;
    position?:
      | "top-left"
      | "top-center"
      | "top-right"
      | "bottom-left"
      | "bottom-center"
      | "bottom-right";
    heading?: string;
    icon?: React.ReactNode;
    bar?: object;
    onClick: (...args: any) => any;
  }

  interface CogoToast {
    success: (msg: string, options?: CogoToastOptions) => Promise<void>;
    info: (msg: string, options?: CogoToastOptions) => Promise<void>;
    loading: (msg: string, options?: CogoToastOptions) => Promise<void>;
    warn: (msg: string, options?: CogoToastOptions) => Promise<void>;
    error: (msg: string, options?: CogoToastOptions) => Promise<void>;
  }
  let ct: CogoToast;
  export default ct;
}
