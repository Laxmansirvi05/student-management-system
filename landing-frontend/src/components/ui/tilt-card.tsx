"use client";

import React, { useRef, useState, useEffect, useCallback } from "react";

interface TiltCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  maxTilt?: number;
  scale?: number;
  glare?: boolean;
  className?: string;
  disabled?: boolean;
}

/**
 * TiltCard provides refined, subtle 3D tilt and glare interaction on hover.
 * Follows Opportunity Radar's design principles: restrained angle, smooth easing,
 * zero layout shift, accessibility-aware (disables on prefers-reduced-motion or touch).
 */
export default function TiltCard({
  children,
  maxTilt = 6,
  scale = 1.018,
  glare = true,
  className = "",
  disabled = false,
  style,
  ...props
}: TiltCardProps) {
  const cardRef = useRef<HTMLDivElement>(null);
  const [transform, setTransform] = useState<string>("perspective(1000px) rotateX(0deg) rotateY(0deg) scale3d(1, 1, 1)");
  const [glarePos, setGlarePos] = useState<{ x: number; y: number; opacity: number }>({ x: 50, y: 50, opacity: 0 });
  const [isHovered, setIsHovered] = useState(false);
  const isReducedMotion = useRef(false);
  const rafId = useRef<number | null>(null);

  useEffect(() => {
    if (typeof window !== "undefined") {
      isReducedMotion.current = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    }
  }, []);

  const handlePointerMove = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if (disabled || isReducedMotion.current || e.pointerType === "touch") return;
      if (!cardRef.current) return;

      const rect = cardRef.current.getBoundingClientRect();
      const clientX = e.clientX;
      const clientY = e.clientY;

      if (rafId.current) cancelAnimationFrame(rafId.current);

      rafId.current = requestAnimationFrame(() => {
        const x = clientX - rect.left;
        const y = clientY - rect.top;
        const centerX = rect.width / 2;
        const centerY = rect.height / 2;

        // Normalized relative coordinates between -1 and 1
        const normX = Math.max(-1, Math.min(1, (x - centerX) / centerX));
        const normY = Math.max(-1, Math.min(1, (y - centerY) / centerY));

        const rotX = -normY * maxTilt;
        const rotY = normX * maxTilt;

        setTransform(
          `perspective(1000px) rotateX(${rotX.toFixed(2)}deg) rotateY(${rotY.toFixed(2)}deg) scale3d(${scale}, ${scale}, 1) translateZ(8px)`
        );

        if (glare) {
          const glareX = Math.round((x / rect.width) * 100);
          const glareY = Math.round((y / rect.height) * 100);
          setGlarePos({ x: glareX, y: glareY, opacity: 1 });
        }
      });
    },
    [disabled, maxTilt, scale, glare]
  );

  const handlePointerEnter = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if (e.pointerType === "touch" || disabled || isReducedMotion.current) return;
      setIsHovered(true);
    },
    [disabled]
  );

  const handlePointerLeave = useCallback(() => {
    if (rafId.current) cancelAnimationFrame(rafId.current);
    setIsHovered(false);
    setTransform("perspective(1000px) rotateX(0deg) rotateY(0deg) scale3d(1, 1, 1) translateZ(0px)");
    if (glare) {
      setGlarePos((prev) => ({ ...prev, opacity: 0 }));
    }
  }, [glare]);

  return (
    <div
      ref={cardRef}
      onPointerEnter={handlePointerEnter}
      onPointerMove={handlePointerMove}
      onPointerLeave={handlePointerLeave}
      style={{
        transform,
        transformStyle: "preserve-3d",
        transition: isHovered
          ? "transform 0.12s cubic-bezier(0.2, 0.8, 0.4, 1), box-shadow 0.35s ease-out, border-color 0.35s ease-out"
          : "transform 0.6s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.6s cubic-bezier(0.16, 1, 0.3, 1), border-color 0.4s ease-out",
        willChange: isHovered ? "transform" : "auto",
        ...style,
      }}
      className={`relative select-none ${className}`}
      {...props}
    >
      {children}

      {/* Subtle cursor-following light glare */}
      {glare && (
        <div
          aria-hidden="true"
          style={{
            background: `radial-gradient(circle 260px at ${glarePos.x}% ${glarePos.y}%, rgba(37,99,235,0.07), transparent 75%)`,
            opacity: glarePos.opacity,
            transition: isHovered ? "opacity 0.25s ease-out" : "opacity 0.6s ease-out",
          }}
          className="pointer-events-none absolute inset-0 rounded-[inherit] z-20"
        />
      )}
    </div>
  );
}
