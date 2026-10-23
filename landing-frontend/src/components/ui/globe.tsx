"use client";

import * as React from "react";
import { useFrame, useThree } from "@react-three/fiber";
import * as THREE from "three";

import {
  SceneContainer,
  type SceneContainerProps,
} from "@/components/ui/globe-utils/scene-container";
import {
  useShadcnTheme,
  type ThemeMode,
} from "@/components/ui/globe-utils/use-shadcn-theme";

/** Evenly distribute `count` points on a unit sphere (Fibonacci sphere). */
function fibonacciSphere(count: number, radius: number) {
  const positions = new Float32Array(count * 3);
  const golden = Math.PI * (3 - Math.sqrt(5));

  for (let i = 0; i < count; i++) {
    const y = 1 - (i / Math.max(count - 1, 1)) * 2;
    const r = Math.sqrt(1 - y * y);
    const theta = golden * i;

    positions[i * 3] = Math.cos(theta) * r * radius;
    positions[i * 3 + 1] = y * radius;
    positions[i * 3 + 2] = Math.sin(theta) * r * radius;
  }

  return positions;
}

interface RotationState {
  currentX: number;
  currentY: number;
  targetX: number;
  targetY: number;
  isDragging: boolean;
  startX: number;
  startY: number;
  startTargetX: number;
  startTargetY: number;
}

/** 
 * Dynamically adjusts camera distance so the complete sphere and all orbital rings (up to 1.25x radius)
 * remain 100% visible and unclipped with comfortable margin across all screen sizes.
 */
function ResponsiveCamera({ radius }: { radius: number }) {
  const { camera, size } = useThree();

  React.useEffect(() => {
    if (!(camera instanceof THREE.PerspectiveCamera)) return;
    const aspect = size.width / Math.max(size.height, 1);
    
    // Outermost orbital ring is at radius * 1.25. Setting bound to 1.30 with 1.03 factor
    // ensures the entire ring sweep is comfortably within viewport without clipping.
    const maxBound = radius * 1.30;
    const halfFovRad = (camera.fov * Math.PI) / 360;

    const distY = maxBound / Math.tan(halfFovRad);
    const distX = maxBound / (Math.tan(halfFovRad) * Math.max(aspect, 0.1));

    const targetZ = Math.max(distX, distY) * 1.03;
    camera.position.z = targetZ;
    camera.updateProjectionMatrix();
  }, [camera, size.width, size.height, radius]);

  return null;
}

function GlobeMesh({
  dots,
  speed,
  size,
  radius = 2.8,
  theme,
  rotationState,
}: {
  dots: number;
  speed: number;
  size: number;
  radius?: number;
  theme: ThemeMode;
  rotationState: React.MutableRefObject<RotationState>;
}) {
  const groupRef = React.useRef<THREE.Group>(null);
  const { primaryColor, mutedColor } = useShadcnTheme(theme);
  const positions = React.useMemo(
    () => fibonacciSphere(dots, radius),
    [dots, radius]
  );

  useFrame((_, delta) => {
    const state = rotationState.current;

    // Automatic slow rotation continues when not interacting
    if (!state.isDragging) {
      state.targetY += delta * 0.25 * speed;
    }

    // Smooth damping interpolation (lerp)
    const damping = Math.min(1, delta * 7.5);
    state.currentX += (state.targetX - state.currentX) * damping;
    state.currentY += (state.targetY - state.currentY) * damping;

    if (groupRef.current) {
      groupRef.current.rotation.x = state.currentX;
      groupRef.current.rotation.y = state.currentY;
    }
  });

  return (
    <group ref={groupRef} rotation={[0.35, 0, 0.15]}>
      {/* Dotted sphere surface - high density and distinct particle size */}
      <points>
        <bufferGeometry>
          <bufferAttribute
            attach="attributes-position"
            args={[positions, 3]}
          />
        </bufferGeometry>

        <pointsMaterial
          color={primaryColor}
          size={size}
          sizeAttenuation
          transparent
          opacity={0.98}
        />
      </points>

      {/* Orbital ring 1: Electric blue */}
      <mesh rotation={[Math.PI / 2, 0, 0]}>
        <torusGeometry args={[radius * 1.15, 0.009, 12, 120]} />
        <meshBasicMaterial
          color={primaryColor}
          transparent
          opacity={0.52}
        />
      </mesh>

      {/* Orbital ring 2: Subtle navy details */}
      <mesh rotation={[Math.PI / 2.4, 0.6, 0]}>
        <torusGeometry args={[radius * 1.25, 0.008, 12, 120]} />
        <meshBasicMaterial
          color={mutedColor}
          transparent
          opacity={0.42}
        />
      </mesh>
    </group>
  );
}

export type GlobeProps = {
  /** Number of surface dots. */
  dots?: number;
  /** Rotation speed multiplier. */
  speed?: number;
  /** Dot size. */
  size?: number;
  /** Sphere radius. Defaults to 2.8 for proportional unclipped rendering. */
  radius?: number;
  className?: string;
  theme?: ThemeMode;
  environment?: SceneContainerProps["environment"];
};

export function Globe({
  dots = 2600,
  speed = 0.3,
  size = 0.054,
  radius = 2.8,
  className,
  theme = "auto",
  environment = "night",
}: GlobeProps) {
  const rotationState = React.useRef<RotationState>({
    currentX: 0.35,
    currentY: 0,
    targetX: 0.35,
    targetY: 0,
    isDragging: false,
    startX: 0,
    startY: 0,
    startTargetX: 0.35,
    startTargetY: 0,
  });

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    rotationState.current.isDragging = true;
    rotationState.current.startX = e.clientX;
    rotationState.current.startY = e.clientY;
    rotationState.current.startTargetX = rotationState.current.targetX;
    rotationState.current.startTargetY = rotationState.current.targetY;

    try {
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    } catch {}
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!rotationState.current.isDragging) return;

    const deltaX = e.clientX - rotationState.current.startX;
    const deltaY = e.clientY - rotationState.current.startY;

    // Smooth drag sensitivity
    rotationState.current.targetY = rotationState.current.startTargetY + deltaX * 0.0055;
    rotationState.current.targetX = Math.max(
      -0.85,
      Math.min(0.85, rotationState.current.startTargetX + deltaY * 0.0055)
    );
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    rotationState.current.isDragging = false;
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {}
  };

  return (
    <div
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      className="relative w-full h-full cursor-grab active:cursor-grabbing touch-none select-none overflow-visible"
    >
      <SceneContainer
        className={className}
        theme={theme}
        environment={environment}
        camera={[0, 0, 8.8]}
        fov={45}
      >
        <ResponsiveCamera radius={radius} />
        <GlobeMesh
          dots={dots}
          speed={speed}
          size={size}
          radius={radius}
          theme={theme}
          rotationState={rotationState}
        />
      </SceneContainer>
    </div>
  );
}

export default Globe;
