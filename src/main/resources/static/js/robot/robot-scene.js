import { h } from "../dom.js";
import { symbol } from "../notes/symbols.js";

// The robot's picture: a Spline 3D scene, with a plain round badge as the stand-in while it loads
// and for good if it cannot load (no WebGL, a slow network, a blocked file). The robot's gestures
// belong to the wrapper in robot.js, so this is a purely visual layer underneath it.

const SCENE_URL = "/robot/robot-scene.splinecode";
const RUNTIME_URL = "/vendor/spline/runtime.js";
// The runtime loads the Draco mesh decoder (and any other WebAssembly it needs) from here, not from a CDN.
const WASM_PATH = "/vendor/spline/wasm";

/**
 * The scene composes the robot with a large transparent margin: measured against its box, the robot
 * itself drew only about half the width. That left a wide dead ring that still accepted a grab, so
 * the robot felt like it only responded on one side. The overdraw makes the canvas physically larger
 * and pulls it back to centre. It is not a transform: scaling would magnify pixels that were already
 * drawn and make the robot soft, while a larger canvas makes the runtime draw more of them.
 */
const SCENE_OVERDRAW = 1.95;

/** The stand-in: a round badge with the robot icon. */
export function robotFallback() {
  const icon = symbol("smart_toy");
  icon.classList.add("rb-fallback__icon");
  return h("div.rb-fallback", icon);
}

/**
 * Loads the scene into `container`. Calls `onMount()` once the canvas is on the page (the original's badge gives way to a blank canvas at that point, not when the scene is drawn), `onLoad()` when it is drawn and `onFail(error)` if the
 * runtime or the scene cannot be loaded. Returns { destroy } to stop it.
 */
export function mountScene(container, { onMount = () => {}, onLoad, onFail }) {
  let canvas = null;
  let application = null;
  let stopped = false;

  (async () => {
    try {
      const runtime = await import(RUNTIME_URL);
      if (stopped) return;
      const size = `${SCENE_OVERDRAW * 100}%`;
      const offset = `${((SCENE_OVERDRAW - 1) / 2) * -100}%`;
      const holder = h("div.rb-scene");
      holder.style.width = size;
      holder.style.height = size;
      holder.style.marginLeft = offset;
      holder.style.marginTop = offset;
      canvas = h("canvas.rb-scene__canvas");
      holder.append(canvas);
      container.append(holder);
      onMount();
      application = new runtime.Application(canvas, { renderOnDemand: true, wasmPath: WASM_PATH });
      await application.load(SCENE_URL);
      if (!stopped) onLoad();
    } catch (error) {
      if (!stopped) onFail(error);
    }
  })();

  return {
    destroy() {
      stopped = true;
      try {
        application?.dispose?.();
      } catch {
        /* the scene is going away anyway */
      }
    },
  };
}
