"use client";

import React, { useRef, useState, useEffect, useCallback } from "react";
import {
  CalendarCheck,
  Clock,
  BookOpen,
  CheckCircle2,
  TrendingUp,
  Sparkles,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import TiltCard from "@/components/ui/tilt-card";

export interface FeatureItem {
  id: string;
  category: string;
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
  highlight: string;
}

export const FEATURES: FeatureItem[] = [
  {
    id: "attendance-marks",
    category: "Academic Records",
    icon: CalendarCheck,
    title: "Attendance, marks and results",
    description:
      "Your attendance in every subject, the marks of each test, your SGPA and CGPA, calculated exactly and flagged before attendance falls below 75%.",
    highlight: "Exact SGPA/CGPA & 75% alerts",
  },
  {
    id: "timetable-notices",
    category: "Daily Schedule",
    icon: Clock,
    title: "Timetable and notices",
    description:
      "Today's classes and room allocations at a glance, the full weekly schedule, and every verified notice and deadline published by your college.",
    highlight: "Live schedule & official circulars",
  },
  {
    id: "study-pyqs",
    category: "Resource Hub",
    icon: BookOpen,
    title: "Study material & PYQs",
    description:
      "Faculty-uploaded notes, previous year question papers, lab manuals and model solutions. Search, preview in-browser, and bookmark items for later.",
    highlight: "In-browser previews & bookmarks",
  },
  {
    id: "quick-exam",
    category: "Practice & Testing",
    icon: CheckCircle2,
    title: "Quick Exam",
    description:
      "Timed practice tests on targeted syllabus units. Submissions are graded securely on the server with instant scores and detailed solution walk-throughs.",
    highlight: "Timed drills & instant grading",
  },
  {
    id: "progress-tracking",
    category: "Performance Analytics",
    icon: TrendingUp,
    title: "Progress Tracking",
    description:
      "See which units are mastered and which need revision, judged from your actual recent practice attempts, complete with direct links to revise.",
    highlight: "Unit mastery & revision guidance",
  },
  {
    id: "ai-assistant",
    category: "AI Study Assistant",
    icon: Sparkles,
    title: "AI Study Assistant",
    description:
      "Contextual explanations, concept summaries and practice questions for your enrolled courses. Isolated and privacy-safe: it never receives your personal records.",
    highlight: "Privacy-safe course explanations",
  },
];

const CARD_COUNT = FEATURES.length;

export default function FeatureCarousel3D() {
  const containerRef = useRef<HTMLDivElement>(null);
  const [renderOffset, setRenderOffset] = useState(0);
  const currentOffset = useRef(0);
  const targetOffset = useRef(0);
  const animFrameId = useRef<number | null>(null);

  // Drag interaction state
  const isDragging = useRef(false);
  const [isDraggingState, setIsDraggingState] = useState(false);
  const dragStartX = useRef(0);
  const dragStartY = useRef(0);
  const dragStartOffset = useRef(0);
  const dragLocked = useRef<"horizontal" | "vertical" | null>(null);
  const pointerHistory = useRef<{ x: number; t: number }[]>([]);

  // Responsive layout state
  const [viewportTier, setViewportTier] = useState<"mobile" | "tablet" | "desktop">("desktop");

  useEffect(() => {
    const handleResize = () => {
      const width = window.innerWidth;
      if (width < 640) {
        setViewportTier("mobile");
      } else if (width < 1024) {
        setViewportTier("tablet");
      } else {
        setViewportTier("desktop");
      }
    };
    handleResize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // Smooth animation to target index
  const animateTo = useCallback((target: number) => {
    if (animFrameId.current) {
      cancelAnimationFrame(animFrameId.current);
    }

    targetOffset.current = target;
    const startVal = currentOffset.current;
    const targetVal = target;
    const startTime = performance.now();
    const distance = Math.abs(targetVal - startVal);
    const duration = Math.min(650, Math.max(340, distance * 280));

    function step(now: number) {
      const elapsed = now - startTime;
      const progress = Math.min(1, elapsed / duration);
      // Smooth cubic deceleration (ease-out)
      const ease = 1 - Math.pow(1 - progress, 3);
      const nextOffset = startVal + (targetVal - startVal) * ease;
      currentOffset.current = nextOffset;
      setRenderOffset(nextOffset);

      if (progress < 1) {
        animFrameId.current = requestAnimationFrame(step);
      } else {
        currentOffset.current = targetVal;
        setRenderOffset(targetVal);
      }
    }

    animFrameId.current = requestAnimationFrame(step);
  }, []);

  // Geometry configuration tailored per viewport
  let radius = 820;
  let angleDeg = 32.5;
  let perspectiveStr = "1550px";
  let cardStride = 420;

  if (viewportTier === "mobile") {
    radius = 480;
    angleDeg = 34.0;
    perspectiveStr = "1050px";
    cardStride = 300;
  } else if (viewportTier === "tablet") {
    radius = 640;
    angleDeg = 33.0;
    perspectiveStr = "1350px";
    cardStride = 360;
  }

  const angleStep = angleDeg * (Math.PI / 180);

  // Pointer event handlers for click-and-drag with touch support
  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (animFrameId.current) {
      cancelAnimationFrame(animFrameId.current);
    }

    isDragging.current = true;
    setIsDraggingState(true);
    dragStartX.current = e.clientX;
    dragStartY.current = e.clientY;
    dragStartOffset.current = currentOffset.current;
    dragLocked.current = null;
    pointerHistory.current = [{ x: e.clientX, t: performance.now() }];

    try {
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    } catch {}
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDragging.current) return;

    const deltaX = e.clientX - dragStartX.current;
    const deltaY = e.clientY - dragStartY.current;

    // Determine gesture direction lock early
    if (!dragLocked.current) {
      if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 8) {
        dragLocked.current = "vertical";
        isDragging.current = false;
        setIsDraggingState(false);
        return;
      }
      if (Math.abs(deltaX) >= Math.abs(deltaY) && Math.abs(deltaX) > 6) {
        dragLocked.current = "horizontal";
      }
    }

    if (dragLocked.current !== "horizontal") return;

    // Track pointer history for momentum calculation
    const now = performance.now();
    pointerHistory.current.push({ x: e.clientX, t: now });
    if (pointerHistory.current.length > 5) {
      pointerHistory.current.shift();
    }

    const sensitivity = 1 / cardStride;
    const newOffset = dragStartOffset.current - deltaX * sensitivity;

    currentOffset.current = newOffset;
    setRenderOffset(newOffset);
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDragging.current) {
      isDragging.current = false;
      setIsDraggingState(false);
      return;
    }
    isDragging.current = false;
    setIsDraggingState(false);

    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {}

    const deltaX = e.clientX - dragStartX.current;

    // Small tap/click snapping
    if (Math.abs(deltaX) < 8) {
      animateTo(Math.round(currentOffset.current));
      return;
    }

    // Momentum
    const history = pointerHistory.current;
    let velocity = 0;
    if (history.length >= 2) {
      const first = history[0];
      const last = history[history.length - 1];
      const dt = last.t - first.t;
      if (dt > 10 && dt < 250) {
        velocity = (last.x - first.x) / dt;
      }
    }

    const indexVelocity = -velocity / (cardStride * 0.0032);
    const clampedMomentum = Math.max(-1.5, Math.min(1.5, indexVelocity * 0.28));
    const snapTarget = Math.round(currentOffset.current + clampedMomentum);
    animateTo(snapTarget);
  };

  const handlePrev = () => {
    const nextTarget = Math.round(currentOffset.current) - 1;
    animateTo(nextTarget);
  };

  const handleNext = () => {
    const nextTarget = Math.round(currentOffset.current) + 1;
    animateTo(nextTarget);
  };

  const handleDotClick = (index: number) => {
    const currentNorm = ((Math.round(currentOffset.current) % CARD_COUNT) + CARD_COUNT) % CARD_COUNT;
    let diff = index - currentNorm;
    if (diff > CARD_COUNT / 2) diff -= CARD_COUNT;
    if (diff < -CARD_COUNT / 2) diff -= CARD_COUNT;
    animateTo(Math.round(currentOffset.current) + diff);
  };

  const handleCardClick = (diff: number) => {
    if (Math.abs(diff) > 0.15) {
      animateTo(Math.round(currentOffset.current) + Math.round(diff));
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "ArrowLeft") {
      e.preventDefault();
      handlePrev();
    } else if (e.key === "ArrowRight") {
      e.preventDefault();
      handleNext();
    }
  };

  const activeIndex =
    ((Math.round(renderOffset) % CARD_COUNT) + CARD_COUNT) % CARD_COUNT;

  return (
    <div
      ref={containerRef}
      tabIndex={0}
      onKeyDown={handleKeyDown}
      role="region"
      aria-label="Student features 3D carousel"
      className="relative w-full outline-none select-none"
    >
      {/* Edge gradient fade masks */}
      <div className="absolute inset-y-0 left-0 w-8 sm:w-20 bg-gradient-to-r from-white via-white/80 to-transparent pointer-events-none z-30" />
      <div className="absolute inset-y-0 right-0 w-8 sm:w-20 bg-gradient-to-l from-white via-white/80 to-transparent pointer-events-none z-30" />

      {/* 3D Stage Container */}
      <div
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        style={{
          perspective: perspectiveStr,
          perspectiveOrigin: "50% 50%",
          touchAction: "pan-y",
        }}
        className="relative w-full h-[400px] sm:h-[440px] md:h-[470px] lg:h-[490px] flex items-center justify-center overflow-hidden cursor-grab active:cursor-grabbing"
      >
        {FEATURES.map((feature, i) => {
          let diff = ((i - renderOffset) % CARD_COUNT + CARD_COUNT) % CARD_COUNT;
          if (diff > CARD_COUNT / 2) diff -= CARD_COUNT;

          const theta = diff * angleStep;
          const x = radius * Math.sin(theta);
          const z = radius * (Math.cos(theta) - 1);
          const rotateY = -theta * (180 / Math.PI) * 0.85;
          const absDiff = Math.abs(diff);

          const scale = Math.max(0.76, 1.05 - 0.17 * Math.min(2.2, absDiff));

          let opacity = 1;
          if (absDiff > 1.25) {
            opacity = Math.max(0, 1 - (absDiff - 1.25) * 0.75);
          }
          if (absDiff > 2.5) {
            opacity = 0;
          }

          const zIndex = Math.round(100 - absDiff * 25);
          const isActive = absDiff < 0.35;
          const IconComponent = feature.icon;

          return (
            <div
              key={feature.id}
              onClick={() => handleCardClick(diff)}
              style={{
                transform: `translate3d(${x}px, 0, ${z}px) rotateY(${rotateY}deg) scale(${scale})`,
                opacity,
                zIndex,
                pointerEvents: opacity < 0.1 ? "none" : "auto",
                willChange: "transform, opacity",
              }}
              className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2
                w-[305px] sm:w-[370px] md:w-[405px] lg:w-[415px] min-h-[305px] sm:min-h-[320px] md:min-h-[335px]"
            >
              <TiltCard
                maxTilt={isActive ? 6 : 4}
                scale={isActive ? 1.02 : 1.01}
                glare={true}
                disabled={isDraggingState || absDiff > 1.2}
                className={`w-full h-full rounded-2xl p-6 sm:p-7 md:p-8 group cursor-pointer
                  transition-[box-shadow,border-color] duration-400
                  flex flex-col justify-between
                  ${
                    isActive
                      ? "bg-white border-2 border-blue-500/70 shadow-[0_20px_50px_rgba(37,99,235,0.14),0_6px_20px_rgba(15,23,42,0.04)] ring-1 ring-blue-500/30"
                      : "bg-white/95 backdrop-blur-sm border border-slate-200/90 shadow-[0_4px_20px_rgba(15,23,42,0.04)] hover:border-blue-300 hover:shadow-[0_16px_36px_rgba(37,99,235,0.08)]"
                  }`}
              >
                {/* Header: Icon & Category Badge */}
                <div className="flex items-center justify-between gap-3">
                  <span
                    className={`flex items-center justify-center w-12 h-12 sm:w-13 sm:h-13 rounded-2xl transition-all duration-300 group-hover:scale-105 group-hover:-translate-y-0.5 ${
                      isActive
                        ? "bg-blue-600 text-white shadow-xs"
                        : "bg-blue-50 text-blue-600 border border-blue-200/70 group-hover:bg-blue-100/70 group-hover:border-blue-300"
                    }`}
                  >
                    <IconComponent className="w-5.5 h-5.5 sm:w-6 sm:h-6" />
                  </span>

                  <span
                    className={`text-[11.5px] sm:text-[12px] font-bold px-3 py-1 sm:px-3.5 sm:py-1 rounded-full uppercase tracking-wider ${
                      isActive
                        ? "bg-blue-50 text-blue-700 border border-blue-200"
                        : "bg-slate-100 text-slate-700 border border-slate-200"
                    }`}
                  >
                    {feature.category}
                  </span>
                </div>

                {/* Title & Description */}
                <div className="my-auto pt-3 sm:pt-4">
                  <h3 className="text-lg sm:text-xl md:text-[22px] font-bold text-[#0F172A] tracking-tight leading-snug group-hover:text-blue-950 transition-colors">
                    {feature.title}
                  </h3>
                  <p className="mt-2.5 text-[14.5px] sm:text-[15px] md:text-[15.5px] text-[#475569] leading-relaxed font-normal">
                    {feature.description}
                  </p>
                </div>

                {/* Footer: Feature highlight pill */}
                <div className="pt-3.5 mt-2 border-t border-slate-100 flex items-center justify-between text-xs sm:text-[13px] font-medium">
                  <span className="truncate pr-3 font-semibold text-slate-700">{feature.highlight}</span>
                  {isActive ? (
                    <span className="flex items-center gap-2 text-blue-600 font-semibold shrink-0">
                      <span className="w-2 h-2 rounded-full bg-blue-600 animate-pulse" />
                      Focused
                    </span>
                  ) : (
                    <span className="text-blue-600 font-semibold group-hover:text-blue-700 group-hover:translate-x-0.5 transition-all shrink-0">
                      Click to view →
                    </span>
                  )}
                </div>
              </TiltCard>
            </div>
          );
        })}
      </div>

      {/* Bottom Controls: Prev Button, Pagination Dots, Next Button */}
      <div className="mt-6 sm:mt-8 flex flex-col items-center gap-3 z-30 relative">
        <div className="flex items-center gap-6">
          <button
            type="button"
            onClick={handlePrev}
            aria-label="Previous card"
            className="w-11 h-11 sm:w-12 sm:h-12 rounded-full border border-slate-200 bg-white/95 hover:bg-white text-slate-700 hover:text-slate-950 flex items-center justify-center shadow-xs hover:shadow-md transition-all cursor-pointer active:scale-95"
          >
            <ChevronLeft className="w-5.5 h-5.5" />
          </button>

          <div className="flex items-center gap-2.5">
            {FEATURES.map((_, i) => (
              <button
                type="button"
                key={i}
                onClick={() => handleDotClick(i)}
                aria-label={`Go to feature ${i + 1}: ${FEATURES[i].title}`}
                className={`h-2.5 transition-all duration-300 rounded-full cursor-pointer ${
                  i === activeIndex
                    ? "w-8 sm:w-9 bg-blue-600 shadow-xs"
                    : "w-2.5 bg-slate-300 hover:bg-slate-400"
                }`}
              />
            ))}
          </div>

          <button
            type="button"
            onClick={handleNext}
            aria-label="Next card"
            className="w-11 h-11 sm:w-12 sm:h-12 rounded-full border border-slate-200 bg-white/95 hover:bg-white text-slate-700 hover:text-slate-950 flex items-center justify-center shadow-xs hover:shadow-md transition-all cursor-pointer active:scale-95"
          >
            <ChevronRight className="w-5.5 h-5.5" />
          </button>
        </div>

        <p className="text-[12.5px] sm:text-[13.5px] text-[#475569] font-medium">
          Drag horizontally or click cards to rotate through all six student features
        </p>
      </div>
    </div>
  );
}
