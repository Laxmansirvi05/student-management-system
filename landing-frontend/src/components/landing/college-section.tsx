"use client";

import React, { useRef } from "react";
import { motion, useInView } from "framer-motion";
import {
  Users,
  ClipboardCheck,
  GraduationCap,
  History,
} from "lucide-react";
import TiltCard from "@/components/ui/tilt-card";

/* ─── Feature data ─────────────────────────────────────────────────────────── */

const COLLEGE_FEATURES = [
  {
    num: "01",
    icon: Users,
    title: "Student accounts",
    desc: "Create, bulk CSV import, deactivate, and reset credentials securely.",
  },
  {
    num: "02",
    icon: ClipboardCheck,
    title: "Attendance and marks",
    desc: "Record attendance for whole class sections and register mid-term exam marks.",
  },
  {
    num: "03",
    icon: GraduationCap,
    title: "Semester results",
    desc: "Publish semester credits and grade points; official SGPA and CGPA update automatically.",
  },
  {
    num: "04",
    icon: History,
    title: "Immutable audit log",
    desc: "Every administrative change to marks, attendance, and accounts is recorded permanently.",
  },
];

/* ─── Single feature card ──────────────────────────────────────────────────── */

function CollegeCard({
  item,
  index,
}: {
  item: (typeof COLLEGE_FEATURES)[number];
  index: number;
}) {
  const Icon = item.icon;

  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-50px" }}
      transition={{ duration: 0.5, delay: 0.08 + index * 0.08, ease: "easeOut" }}
      className="h-full"
    >
      <TiltCard
        maxTilt={6}
        scale={1.02}
        glare={true}
        className="h-full rounded-2xl group cursor-pointer"
      >
        <div
          className="relative h-full rounded-2xl bg-white/85 backdrop-blur-md
                      border border-slate-200/80
                      p-6 sm:p-7
                      shadow-[0_2px_8px_rgba(15,23,42,0.04)]
                      group-hover:shadow-[0_16px_40px_rgba(37,99,235,0.09),0_2px_8px_rgba(15,23,42,0.04)]
                      group-hover:border-blue-300/80
                      transition-[border-color,box-shadow] duration-400
                      overflow-hidden flex flex-col justify-between"
        >
          {/* Subtle top accent line */}
          <div
            className="absolute top-0 left-6 right-6 h-[1.5px]
                        bg-gradient-to-r from-transparent via-blue-500/50 to-transparent
                        opacity-0 group-hover:opacity-100 transition-opacity duration-400"
          />

          <div>
            {/* Header row: number + icon */}
            <div className="flex items-center justify-between mb-5">
              <span
                className="text-[12px] font-bold tracking-[0.18em] text-slate-600
                            group-hover:text-blue-600 transition-colors duration-300"
              >
                {item.num}
              </span>
              <span
                className="flex items-center justify-center w-10 h-10 rounded-xl
                            bg-slate-100/90 border border-slate-200/90
                            group-hover:bg-blue-50 group-hover:border-blue-200
                            group-hover:scale-105
                            transition-all duration-300 shadow-xs"
              >
                <Icon
                  className="w-[18px] h-[18px] text-slate-800
                              group-hover:text-blue-600 transition-colors duration-300"
                />
              </span>
            </div>

            {/* Title */}
            <h3
              className="text-[18px] sm:text-[19px] font-bold text-[#0F172A] tracking-tight
                          leading-snug mb-2.5 group-hover:text-blue-950 transition-colors"
            >
              {item.title}
            </h3>

            {/* Description: darker blue-gray, ~15-16px, comfortable line-height 1.6 */}
            <p className="text-[15px] sm:text-[15.5px] text-slate-600 leading-[1.6] font-normal">
              {item.desc}
            </p>
          </div>

          {/* Micro status indicator: darker and well-defined */}
          <div className="mt-5 pt-3.5 border-t border-slate-100 flex items-center justify-between text-xs font-semibold text-slate-600 group-hover:text-blue-600 transition-colors">
            <span className="text-[12px] font-semibold tracking-wider uppercase">Institutional Record</span>
            <span className="opacity-0 group-hover:opacity-100 transition-opacity duration-300 text-blue-600 font-bold text-[12.5px]">
              Protected →
            </span>
          </div>
        </div>
      </TiltCard>
    </motion.div>
  );
}

/* ─── Section ──────────────────────────────────────────────────────────────── */

export default function CollegeSection() {
  const sectionRef = useRef<HTMLElement>(null);
  const isInView = useInView(sectionRef, { once: true, margin: "-60px" });

  return (
    <section
      id="college"
      ref={sectionRef}
      className="relative py-14 sm:py-18 lg:py-20 px-6 lg:px-12 z-20 border-t border-slate-200/60"
    >
      <div className="max-w-[1360px] mx-auto">
        {/* ── Two-column layout ─────────────────────────────────────────── */}
        <div className="flex flex-col lg:flex-row gap-10 lg:gap-16 items-start">
          {/* Left: header copy — sticky on desktop for comfortable reading */}
          <div className="lg:w-[38%] lg:sticky lg:top-28 flex flex-col items-start">
            <motion.span
              initial={{ opacity: 0, y: 10 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.45 }}
              className="text-[11.5px] sm:text-[12px] font-bold uppercase tracking-wider
                          text-blue-600 bg-blue-50/90 border border-blue-200/70
                          px-3.5 py-1.5 rounded-full inline-block"
            >
              For colleges
            </motion.span>

            <motion.h2
              initial={{ opacity: 0, y: 16 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.55, delay: 0.06 }}
              className="mt-4 text-3xl sm:text-4xl lg:text-[42px] font-bold tracking-tight
                          text-[#0F172A] leading-[1.15]"
            >
              The records stay with the college.
            </motion.h2>

            <motion.p
              initial={{ opacity: 0, y: 12 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.55, delay: 0.12 }}
              className="mt-4 text-[16px] sm:text-[17px] text-slate-700 leading-[1.65] font-normal"
            >
              Administrators manage students, attendance, marks, timetables and
              resources centrally. Students only view their own records; what
              they may edit is their phone number, bio and photo.
            </motion.p>

            {/* Subtle decorative connecting accent */}
            <motion.div
              initial={{ opacity: 0, scaleY: 0 }}
              animate={isInView ? { opacity: 1, scaleY: 1 } : {}}
              transition={{ duration: 0.7, delay: 0.3 }}
              className="hidden lg:block mt-8 w-[1px] h-20 ml-4
                          bg-gradient-to-b from-blue-400/35 via-blue-300/15 to-transparent origin-top"
            />
          </div>

          {/* Right: 2×2 card grid */}
          <div className="lg:w-[62%] grid grid-cols-1 sm:grid-cols-2 gap-5 w-full">
            {COLLEGE_FEATURES.map((f, i) => (
              <CollegeCard key={f.num} item={f} index={i} />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
