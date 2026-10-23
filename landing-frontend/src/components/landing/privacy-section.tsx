"use client";

import React, { useRef } from "react";
import { motion, useInView } from "framer-motion";
import {
  KeyRound,
  ShieldCheck,
  FileText,
  Sparkles,
  ArrowRight,
} from "lucide-react";
import TiltCard from "@/components/ui/tilt-card";

/* ─── Feature data ─────────────────────────────────────────────────────────── */

const PRIVACY_FEATURES = [
  {
    icon: KeyRound,
    title: "Passwords stay secret",
    desc: "Hashed with BCrypt. Nobody can retrieve or decrypt your password, not even system administrators.",
    accentFrom: "from-blue-500/15",
    accentTo: "to-indigo-500/10",
  },
  {
    icon: ShieldCheck,
    title: "Strict data isolation",
    desc: "The server authenticates and authorizes every query. Another student cannot access your marks or records.",
    accentFrom: "from-blue-500/15",
    accentTo: "to-sky-500/10",
  },
  {
    icon: FileText,
    title: "Private storage links",
    desc: "Learning resources sit in secure private storage and open through signed URLs that expire after 5 minutes.",
    accentFrom: "from-indigo-500/15",
    accentTo: "to-blue-500/10",
  },
  {
    icon: Sparkles,
    title: "AI without student data",
    desc: "The AI assistant only processes the exact text query you submit. Personal records are never shared with AI.",
    accentFrom: "from-sky-500/15",
    accentTo: "to-blue-500/10",
  },
];

/* ─── Single privacy card ──────────────────────────────────────────────────── */

function PrivacyCard({
  item,
  index,
}: {
  item: (typeof PRIVACY_FEATURES)[number];
  index: number;
}) {
  const Icon = item.icon;

  return (
    <motion.div
      initial={{ opacity: 0, y: 22 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-40px" }}
      transition={{ duration: 0.5, delay: 0.06 + index * 0.07, ease: "easeOut" }}
      className="h-full"
    >
      <TiltCard
        maxTilt={6}
        scale={1.02}
        glare={true}
        className="h-full rounded-2xl group cursor-pointer"
      >
        <div
          className="relative h-full rounded-2xl
                      bg-white/85 backdrop-blur-md
                      border border-slate-200/80
                      shadow-[0_2px_8px_rgba(15,23,42,0.04)]
                      group-hover:shadow-[0_16px_40px_rgba(37,99,235,0.09),0_2px_8px_rgba(15,23,42,0.04)]
                      group-hover:border-blue-300/80
                      transition-[border-color,box-shadow] duration-400
                      overflow-hidden flex flex-col justify-between"
        >
          {/* Top gradient accent bar */}
          <div
            className={`h-[2px] w-full bg-gradient-to-r ${item.accentFrom} via-blue-500/20 ${item.accentTo}
                        opacity-70 group-hover:opacity-100 transition-opacity duration-400`}
          />

          <div className="p-6 sm:p-7 pt-5 sm:pt-6 flex flex-col h-full justify-between">
            <div>
              {/* Icon container with layered depth */}
              <div className="relative mb-5">
                <div
                  className="absolute -inset-1 rounded-2xl bg-blue-500/10 blur-md
                              opacity-0 group-hover:opacity-100 transition-opacity duration-400"
                />
                <span
                  className="relative flex items-center justify-center w-11 h-11 rounded-xl
                              bg-gradient-to-br from-blue-50 to-slate-50
                              border border-blue-200/60
                              group-hover:border-blue-300/80
                              group-hover:from-blue-50 group-hover:to-blue-100/50
                              group-hover:scale-105
                              transition-all duration-300
                              shadow-[0_1px_3px_rgba(37,99,235,0.06)]"
                >
                  <Icon
                    className="w-[18px] h-[18px] text-blue-600
                                transition-colors duration-300"
                  />
                </span>
              </div>

              {/* Title: 18-19px, bold dark navy */}
              <h3
                className="text-[18px] sm:text-[19px] font-bold text-[#0F172A] tracking-tight
                            leading-snug mb-2.5 group-hover:text-blue-950 transition-colors"
              >
                {item.title}
              </h3>

              {/* Description: 15-15.5px, darker blue-gray, comfortable line height */}
              <p className="text-[15px] sm:text-[15.5px] text-slate-600 leading-[1.6] font-normal">
                {item.desc}
              </p>
            </div>

            {/* Bottom trust tag: darker and distinct */}
            <div className="mt-5 pt-3.5 border-t border-slate-100 flex items-center justify-between text-xs font-semibold text-slate-600 group-hover:text-blue-600 transition-colors">
              <span className="text-[12px] font-semibold tracking-wider uppercase">Zero Leakage</span>
              <span className="w-2 h-2 rounded-full bg-blue-600 opacity-70 group-hover:opacity-100 transition-opacity" />
            </div>
          </div>
        </div>
      </TiltCard>
    </motion.div>
  );
}

/* ─── Section ──────────────────────────────────────────────────────────────── */

export default function PrivacySection() {
  const sectionRef = useRef<HTMLElement>(null);
  const isInView = useInView(sectionRef, { once: true, margin: "-60px" });

  return (
    <section
      id="privacy"
      ref={sectionRef}
      className="relative py-14 sm:py-18 lg:py-20 px-6 lg:px-12 z-20 border-t border-slate-200/60"
    >
      <div className="max-w-[1360px] mx-auto">
        {/* ── Section header ─────────────────────────────────────────────── */}
        <div className="mb-10 sm:mb-12 text-center max-w-3xl mx-auto">
          <motion.span
            initial={{ opacity: 0, y: 10 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.45 }}
            className="text-[16px] sm:text-[17px] font-bold uppercase tracking-wider
                        text-blue-600 bg-blue-50/90 border border-blue-200/70
                        px-5 py-2 rounded-full inline-block"
          >
            Privacy &amp; Security
          </motion.span>

          <motion.h2
            initial={{ opacity: 0, y: 16 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.55, delay: 0.06 }}
            className="mt-4 text-3xl sm:text-4xl lg:text-[42px] font-bold tracking-tight
                        text-[#0F172A] leading-[1.15]"
          >
            Built to keep your records yours.
          </motion.h2>

          <motion.p
            initial={{ opacity: 0, y: 12 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.55, delay: 0.12 }}
            className="mt-4 text-[16px] sm:text-[17px] text-slate-700 leading-[1.65] font-normal max-w-2xl mx-auto"
          >
            Every layer of the application is designed around data minimisation,
            strict isolation, and server-side authorization.
          </motion.p>
        </div>

        {/* ── Four-column card grid ──────────────────────────────────────── */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 sm:gap-6">
          {PRIVACY_FEATURES.map((f, i) => (
            <PrivacyCard key={f.title} item={f} index={i} />
          ))}
        </div>

        {/* ── Privacy policy link ─────────────────────────────────────────── */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.45, delay: 0.25 }}
          className="mt-10 sm:mt-12 text-center"
        >
          <a
            href="/privacy.html"
            className="inline-flex items-center gap-2.5
                        text-[15px] font-semibold
                        text-blue-600 hover:text-blue-700
                        border border-blue-200/70 hover:border-blue-300
                        bg-blue-50/60 hover:bg-blue-50/90
                        rounded-full px-6 py-2.5
                        transition-all duration-300 group
                        shadow-[0_1px_3px_rgba(37,99,235,0.06)]
                        hover:shadow-[0_4px_14px_rgba(37,99,235,0.14)]"
          >
            Read our complete privacy policy
            <ArrowRight className="w-4 h-4 transition-transform duration-200 group-hover:translate-x-1" />
          </a>
        </motion.div>
      </div>
    </section>
  );
}
