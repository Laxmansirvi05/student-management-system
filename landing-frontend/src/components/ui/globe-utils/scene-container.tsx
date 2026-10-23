"use client";

import * as React from "react";
import { Canvas } from "@react-three/fiber";
import type { ThemeMode } from "./use-shadcn-theme";
import { cn } from "@/lib/utils";

export interface SceneContainerProps {
  children?: React.ReactNode;
  className?: string;
  theme?: ThemeMode;
  environment?: string;
  camera?: [number, number, number];
  fov?: number;
}

export function SceneContainer({
  children,
  className,
  camera = [0, 0, 7.8],
  fov = 45,
}: SceneContainerProps) {
  return (
    <div className={cn("relative w-full h-full min-h-[350px] overflow-visible", className)}>
      <Canvas
        camera={{
          position: camera,
          fov: fov,
        }}
        gl={{
          alpha: true,
          antialias: true,
          powerPreference: "high-performance",
        }}
        onCreated={({ gl }) => {
          gl.setClearColor(0x000000, 0);
        }}
        style={{
          background: "transparent",
          pointerEvents: "auto",
        }}
        dpr={[1, 2]}
      >
        <ambientLight intensity={1.5} />
        {children}
      </Canvas>
    </div>
  );
}

export default SceneContainer;
