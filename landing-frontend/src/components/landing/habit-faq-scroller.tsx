"use client";

import React, { useRef } from "react";
import { motion, useInView } from "framer-motion";

// --- FAQ Data (Exactly 2 rows, 3 questions per row) ---

const ROW_1 = [
  {
    q: "How are attendance, marks and results calculated?",
    a: "Your attendance percentage is computed per subject with required 75% thresholds flagged early. Test marks, SGPA and CGPA are calculated using standard semester grade points with explicit HALF_UP rounding.",
  },
  {
    q: "Where can I view class timetables and college notices?",
    a: "Today's scheduled classes, room numbers, the full weekly timetable, and official college notices with academic deadlines are organized in one clear student dashboard.",
  },
  {
    q: "What study resources can I access?",
    a: "You can search, preview and bookmark syllabus-aligned notes, past examination question papers (PYQs), lab manuals and model papers uploaded by college faculty.",
  },
];

const ROW_2 = [
  {
    q: "How does Quick Exam practice work?",
    a: "Quick Exam offers timed practice tests on specific course units. Answers are evaluated securely on the server with immediate scores and question-by-question explanations.",
  },
  {
    q: "Can I track my learning progress across units?",
    a: "Yes. Progress tracking analyzes your recent quiz attempts to highlight strong topics, identify areas needing revision, and recommend targeted study notes.",
  },
  {
    q: "Does the AI study assistant access my college records?",
    a: "No. The AI study assistant only sees the specific question or topic you submit for that request. Your student identity, profile, marks, and official college records are never sent to the AI provider.",
  },
];

// --- Components ---

function FaqCard({ q, a }: { q: string; a: string }) {
  return (
    <motion.div
      whileHover={{ scale: 1.02, y: -3 }}
      transition={{ duration: 0.25, ease: "easeOut" }}
      className="flex-shrink-0 w-[300px] sm:w-[380px] md:w-[440px] bg-white/90 backdrop-blur-md rounded-2xl p-6 sm:p-7
                 border border-slate-200/80 shadow-[0_2px_12px_rgba(15,23,42,0.04)]
                 hover:shadow-[0_14px_36px_rgba(37,99,235,0.09)] hover:border-blue-300/80 transition-all duration-300
                 flex flex-col gap-3 whitespace-normal cursor-default"
    >
      <h3 className="text-lg sm:text-[19px] font-bold text-[#0F172A] leading-snug tracking-tight">
        {q}
      </h3>
      <p className="text-[14px] sm:text-[14.5px] text-slate-600 leading-[1.6] font-normal">
        {a}
      </p>
    </motion.div>
  );
}

function HorizontalScroller({
  items,
  direction,
  speed,
}: {
  items: { q: string; a: string }[];
  direction: "left" | "right";
  speed: number;
}) {
  const duplicatedItems = [...items, ...items, ...items, ...items];
  const xValues = direction === "left" ? ["0%", "-50%"] : ["-50%", "0%"];

  return (
    <div className="relative flex w-full overflow-hidden py-3 -my-3">
      {/* Edge gradient fade masks */}
      <div 
        className="absolute inset-y-0 left-0 w-[8%] md:w-[12%] bg-gradient-to-r from-white via-white/80 to-transparent z-10 pointer-events-none" 
      />
      <div 
        className="absolute inset-y-0 right-0 w-[8%] md:w-[12%] bg-gradient-to-l from-white via-white/80 to-transparent z-10 pointer-events-none" 
      />
      
      <motion.div
        className="flex gap-5 md:gap-6 px-3 md:px-4"
        animate={{ x: xValues }}
        transition={{
          repeat: Infinity,
          ease: "linear",
          duration: speed,
        }}
      >
        {duplicatedItems.map((item, idx) => (
          <FaqCard key={`${item.q}-${idx}`} q={item.q} a={item.a} />
        ))}
      </motion.div>
    </div>
  );
}

export default function FaqSection() {
  const ref = useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, margin: "-60px" });

  return (
    <section id="faq" ref={ref} className="relative w-full py-14 sm:py-18 lg:py-20 overflow-hidden z-20 border-t border-slate-200/60">
      <div className="max-w-[1360px] mx-auto px-6 lg:px-12 mb-8 sm:mb-10 flex flex-col items-center text-center">
        <motion.h2 
          initial={{ opacity: 0, y: 16 }}
          animate={isInView ? { opacity: 1, y: 0 } : { opacity: 0, y: 16 }}
          transition={{ duration: 0.55, ease: "easeOut" }}
          className="text-3xl sm:text-4xl lg:text-[42px] font-bold tracking-tight text-[#0F172A] leading-[1.15] text-center"
        >
          Frequently Asked Questions
        </motion.h2>
        <motion.p 
          initial={{ opacity: 0, y: 12 }}
          animate={isInView ? { opacity: 1, y: 0 } : { opacity: 0, y: 12 }}
          transition={{ duration: 0.55, ease: "easeOut", delay: 0.08 }}
          className="mt-4 sm:mt-5 text-[16px] sm:text-[17px] text-slate-700 max-w-2xl leading-[1.65] font-normal"
        >
          Everything you need to know about Campus Companion, student records, learning tools, and privacy protection.
        </motion.p>
      </div>

      <div className="flex flex-col gap-5 sm:gap-6">
        <HorizontalScroller items={ROW_1} direction="left" speed={45} />
        <HorizontalScroller items={ROW_2} direction="right" speed={50} />
      </div>
    </section>
  );
}
