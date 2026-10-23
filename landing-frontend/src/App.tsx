"use client";

import React from "react";
import {
  GraduationCap,
  ArrowRight,
} from "lucide-react";
import KineticGrid from "@/components/landing/kinetic-grid";
import Globe from "@/components/ui/globe";
import FeatureCarousel3D from "@/components/landing/feature-carousel-3d";
import CollegeSection from "@/components/landing/college-section";
import PrivacySection from "@/components/landing/privacy-section";
import FaqSection from "@/components/landing/habit-faq-scroller";
import HoverFooter from "@/components/landing/hover-footer";

export default function App() {
  const [currentUser, setCurrentUser] = React.useState<{
    role: string;
    mustChangePassword?: boolean;
  } | null>(null);

  React.useEffect(() => {
    fetch("/api/v1/auth/me", { credentials: "same-origin" })
      .then((res) => {
        if (res.ok) return res.json();
        return null;
      })
      .then((user) => {
        if (user) {
          setCurrentUser(user);
          // If already signed in, seamlessly redirect to their dashboard (FR-UX-002)
          if (user.mustChangePassword) {
            window.location.replace("/change-password.html");
          } else if (user.role === "ADMIN") {
            window.location.replace("/admin/dashboard.html");
          } else {
            window.location.replace("/student/dashboard.html");
          }
        }
      })
      .catch(() => {
        // Unauthenticated visitor or offline: stay on landing page
      });
  }, []);

  const dashboardUrl = currentUser
    ? currentUser.role === "ADMIN"
      ? "/admin/dashboard.html"
      : "/student/dashboard.html"
    : "/login.html";

  return (
    <KineticGrid>
      {/* ── Top Navigation ──────────────────────────────────────────────── */}
      <header className="absolute top-0 left-0 right-0 w-full z-30">
        <nav className="max-w-[1440px] mx-auto flex items-center justify-between px-6 lg:px-12 py-5 sm:py-6">
          {/* Left: Brand */}
          <a
            href="/"
            className="flex items-center gap-3 font-bold text-slate-950 text-[21px] tracking-tight hover:opacity-90 transition-opacity"
          >
            <span className="flex items-center justify-center w-9 h-9 rounded-xl bg-blue-600/10 text-blue-600 border border-blue-500/20 shadow-xs">
              <GraduationCap className="w-5 h-5 text-blue-600" />
            </span>
            <span>Campus Companion</span>
          </a>

          {/* Center: Navigation Links */}
          <div className="hidden md:flex items-center gap-8 text-[15.5px] font-medium text-slate-700">
            <a href="#features" className="hover:text-blue-600 transition-colors">
              Features
            </a>
            <a href="#college" className="hover:text-blue-600 transition-colors">
              For colleges
            </a>
            <a href="#privacy" className="hover:text-blue-600 transition-colors">
              Privacy
            </a>
            <a href="#faq" className="hover:text-blue-600 transition-colors">
              FAQ
            </a>
          </div>

          {/* Right: Login Action */}
          <div className="flex items-center gap-4">
            <a
              href={dashboardUrl}
              className="rounded-full bg-slate-950 px-6 py-2.5 text-[14.5px] font-semibold text-white hover:bg-slate-800 transition-all shadow-xs hover:shadow-md active:scale-98"
            >
              {currentUser ? "Dashboard" : "Log in"}
            </a>
          </div>
        </nav>
      </header>

      {/* ── Hero Section ────────────────────────────────────────────────── */}
      <div className="flex min-h-[calc(100vh-4rem)] lg:min-h-[720px] flex-col lg:flex-row items-center justify-between max-w-[1440px] mx-auto px-6 lg:px-12 pt-24 pb-8 sm:pt-28 sm:pb-12 z-10 relative">
        {/* Left Side: Content with refined typography & whitespace */}
        <div className="w-full lg:w-[48%] min-w-0 flex flex-col items-start text-left mt-6 lg:mt-0">
          <span className="rounded-full border border-blue-200/80 px-4.5 py-1.5 text-[13px] font-bold tracking-wide uppercase text-blue-600 bg-blue-50/90 backdrop-blur-md shadow-xs">
            A clearer view of college life
          </span>

          {/* Unified dark navy headline */}
          <h1
            style={{ fontSize: "clamp(3.1rem, 5.6vw, 5.2rem)" }}
            className="mt-6 w-full font-bold tracking-[-0.035em] text-[#0F172A] leading-[1.04] text-balance"
          >
            Your College Life, All in One Place.
          </h1>

          <div className="mt-7 flex max-w-[560px] flex-col gap-3 text-[18px] sm:text-[19.5px] font-normal text-[#334155] leading-relaxed">
            <p>
              Keep attendance, marks, classes and college notices close to the resources and practice tools that help you study.
            </p>
          </div>

          <div className="mt-8 sm:mt-9 flex flex-wrap items-center gap-4">
            <a
              href={dashboardUrl}
              className="inline-flex items-center gap-2.5 rounded-full bg-slate-950 px-8 py-4 text-[15.5px] font-semibold text-white hover:bg-slate-800 transition-all shadow-sm hover:shadow-md active:scale-98"
            >
              {currentUser ? "Open Dashboard" : "Log in"}
              <ArrowRight className="w-4.5 h-4.5" />
            </a>
            <a
              href="#features"
              className="rounded-full border border-slate-300/90 bg-white/80 backdrop-blur-sm px-8 py-4 text-[15.5px] font-semibold text-slate-800 hover:bg-white hover:border-slate-400 transition-all shadow-xs active:scale-98"
            >
              See what it does
            </a>
          </div>

          <p className="mt-6 text-[14px] sm:text-[14.5px] font-medium text-slate-700">
            Your college creates accounts. There is no public sign-up.
          </p>
        </div>

        {/* Right Side: Interactive 3D Dotted Globe (Proportionally unclipped with intentional label placement) */}
        <div className="w-full lg:w-[50%] xl:w-[52%] flex items-center justify-center h-[540px] sm:h-[620px] lg:h-[700px] xl:h-[740px] relative mt-8 lg:mt-0 overflow-visible">
          {/* Orbital labels placed intentionally along the orbital sweeps */}
          <div className="absolute top-[12%] left-[8%] sm:left-[12%] z-20 flex items-center gap-2 rounded-full border border-blue-200/80 bg-white/95 px-3.5 py-1.5 text-[12.5px] font-semibold text-slate-800 shadow-[0_4px_16px_rgba(15,23,42,0.06)] backdrop-blur-md pointer-events-none select-none">
            <span className="w-2 h-2 rounded-full bg-blue-600 animate-pulse shrink-0" />
            Classes
          </div>

          <div className="absolute top-[14%] right-[6%] sm:right-[10%] z-20 flex items-center gap-2 rounded-full border border-blue-200/80 bg-white/95 px-3.5 py-1.5 text-[12.5px] font-semibold text-slate-800 shadow-[0_4px_16px_rgba(15,23,42,0.06)] backdrop-blur-md pointer-events-none select-none">
            <span className="w-2 h-2 rounded-full bg-blue-500 shrink-0" />
            Attendance
          </div>

          <div className="absolute bottom-[16%] left-[7%] sm:left-[11%] z-20 flex items-center gap-2 rounded-full border border-blue-200/80 bg-white/95 px-3.5 py-1.5 text-[12.5px] font-semibold text-slate-800 shadow-[0_4px_16px_rgba(15,23,42,0.06)] backdrop-blur-md pointer-events-none select-none">
            <span className="w-2 h-2 rounded-full bg-blue-600 shrink-0" />
            Exams
          </div>

          <div className="absolute bottom-[13%] right-[6%] sm:right-[10%] z-20 flex items-center gap-2 rounded-full border border-blue-200/80 bg-white/95 px-3.5 py-1.5 text-[12.5px] font-semibold text-slate-800 shadow-[0_4px_16px_rgba(15,23,42,0.06)] backdrop-blur-md pointer-events-none select-none">
            <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse shrink-0" />
            AI Study Assistant
          </div>

          {/* Interactive Fibonacci Dotted Three.js Globe */}
          <Globe
            dots={2600}
            speed={0.3}
            size={0.054}
            radius={2.8}
            theme="auto"
            className="w-full h-full max-w-[780px] max-h-[780px] z-10"
          />
        </div>
      </div>

      {/* ── Student Features 3D Carousel Section ──────────────────────────── */}
      <section id="features" className="relative py-14 sm:py-18 px-6 lg:px-12 z-20 border-t border-slate-200/60">
        <div className="max-w-[1360px] mx-auto">
          <div className="mb-8 sm:mb-10 text-center max-w-3xl mx-auto">
            <span className="text-[11.5px] sm:text-[12px] font-bold uppercase tracking-wider text-blue-600 bg-blue-50/90 border border-blue-200/70 px-3.5 py-1.5 rounded-full inline-block">
              For students
            </span>
            <h2 className="mt-4 text-3xl sm:text-4xl lg:text-[42px] font-bold tracking-tight text-[#0F172A] leading-[1.15]">
              Everything you check, and everything you study, together.
            </h2>
            <p className="mt-4 text-base sm:text-[16.5px] text-[#475569] leading-relaxed font-normal max-w-2xl mx-auto">
              Attendance, timetables, syllabus notes, practice tests, and an AI study assistant unified in a single student account.
            </p>
          </div>

          <FeatureCarousel3D />
        </div>
      </section>

      {/* ── For Colleges Section ─────────────────────────────────────────── */}
      <CollegeSection />

      {/* ── Privacy & Security Section ───────────────────────────────────── */}
      <PrivacySection />

      {/* ── FAQ Section (Exactly Two Rows) ──────────────────────────────── */}
      <FaqSection />

      {/* ── Footer ──────────────────────────────────────────────────────── */}
      <HoverFooter />
    </KineticGrid>
  );
}
