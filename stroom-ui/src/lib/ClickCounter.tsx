export type Handler = (x: any) => void;

class ClickCounter {
  delay: number;
  prevent: boolean;
  onSingleClickHandler: Handler;
  onDoubleClickHandler: Handler;
  timer: NodeJS.Timer | undefined;

  constructor() {
    this.timer = undefined;
    this.delay = 200;
    this.prevent = false;
  }

  withOnSingleClick(handler: Handler) {
    this.onSingleClickHandler = handler;
    return this;
  }

  withOnDoubleClick(handler: Handler) {
    this.onDoubleClickHandler = handler;
    return this;
  }

  onSingleClick(props: any) {
    this.timer = setTimeout(() => {
      if (!this.prevent) {
        this.onSingleClickHandler(props);
      }
      this.prevent = false;
    }, this.delay);
  }

  onDoubleClick(props: any) {
    if (this.timer) {
      clearTimeout(this.timer);
    }
    this.prevent = true;
    this.onDoubleClickHandler(props);
  }
}

export default ClickCounter;
