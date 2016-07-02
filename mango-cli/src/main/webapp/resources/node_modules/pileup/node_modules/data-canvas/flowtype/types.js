// These are type declarations for use with Flow
// http://flowtype.org/

declare module "data-canvas" {

  declare class DataCanvasRenderingContext2D extends CanvasRenderingContext2D {
    pushObject(o: any): void;
    popObject(): void;
    reset(): void;
  }

  declare function getDataContext(ctx: CanvasRenderingContext2D): DataCanvasRenderingContext2D;
  declare function getDataContext(canvas: HTMLCanvasElement): DataCanvasRenderingContext2D;

  declare class DataContext extends DataCanvasRenderingContext2D {
    constructor(ctx: CanvasRenderingContext2D): void;
  }

  declare class RecordingContext extends DataCanvasRenderingContext2D {
    constructor(ctx: CanvasRenderingContext2D): void;
    calls: Object[];
    drawnObjectsWith(predicate: (o: Object)=>boolean): Object[];
    callsOf(type: string): Array<any>[];

    static recordAll(): void;
    static reset(): void;

    static drawnObjectsWith(div: HTMLElement, selector: string, predicate:(o: Object)=>boolean): Object[];
    static drawnObjectsWith(predicate:(o: Object)=>boolean): Object[];

    static drawnObjects(div: HTMLElement, selector: string): Object[];
    static drawnObjects(): Object[];

    static callsOf(div: HTMLElement, selector: string, type: string): Array<any>[];
    static callsOf(type: string): Array<any>[];
  }

  declare class ClickTrackingContext extends DataCanvasRenderingContext2D {
    constructor(ctx: CanvasRenderingContext2D, x: number, y: number): void;
    hit: ?any[];
    hits: any[][];
  }

}
