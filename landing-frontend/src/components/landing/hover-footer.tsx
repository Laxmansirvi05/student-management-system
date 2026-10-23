"use client";

import React from "react";
import { GraduationCap, ShieldCheck, Mail, BookOpen } from "lucide-react";

export default function HoverFooter() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="relative w-full bg-[#07080A] text-slate-300 pt-16 pb-8 overflow-hidden z-20 border-t border-slate-900/60">
      <div className="max-w-[1360px] mx-auto px-6 lg:px-12 relative z-10">
        
        {/* Top 4-Column Layout */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8 lg:gap-8 mb-12 sm:mb-14">
          
          {/* Column 1: Brand */}
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-2.5">
              <span className="flex items-center justify-center w-8 h-8 rounded-lg bg-blue-600/20 text-blue-400 border border-blue-500/30">
                <GraduationCap className="w-4 h-4 text-blue-400" />
              </span>
              <h3 className="text-xl font-bold text-white tracking-tight">Campus Companion</h3>
            </div>
            <p className="text-sm text-slate-400 leading-relaxed max-w-xs">
              A unified student management and learning platform keeping attendance, marks, timetables, syllabus notes, practice tests and an AI assistant together.
            </p>
          </div>

          {/* Column 2: Student Tools */}
          <div className="flex flex-col gap-4 lg:pl-4">
            <h4 className="text-sm font-semibold text-white uppercase tracking-wider">Features</h4>
            <div className="grid grid-cols-1 gap-y-2.5">
              {[
                { name: "Attendance & Marks", href: "#features" },
                { name: "Class Timetable & Notices", href: "#features" },
                { name: "Study Material & PYQs", href: "#features" },
                { name: "Quick Exam Practice", href: "#features" },
                { name: "Unit Progress Tracking", href: "#features" },
                { name: "AI Study Assistant", href: "#features" },
              ].map((link) => (
                <a
                  key={link.name}
                  href={link.href}
                  className="text-sm text-slate-400 hover:text-blue-400 transition-colors duration-200"
                >
                  {link.name}
                </a>
              ))}
            </div>
          </div>

          {/* Column 3: Resources & Legal */}
          <div className="flex flex-col gap-4 lg:pl-6">
            <h4 className="text-sm font-semibold text-white uppercase tracking-wider">Resources</h4>
            <ul className="flex flex-col gap-2.5">
              {[
                { name: "Frequently Asked Questions", href: "#faq" },
                { name: "Privacy Policy", href: "/privacy.html" },
                { name: "Terms of Use", href: "/terms.html" },
                { name: "Student Login", href: "/login.html" },
              ].map((link) => (
                <li key={link.name}>
                  <a
                    href={link.href}
                    className="text-sm text-slate-400 hover:text-blue-400 transition-colors duration-200"
                  >
                    {link.name}
                  </a>
                </li>
              ))}
            </ul>
          </div>

          {/* Column 4: Institutional Access */}
          <div className="flex flex-col gap-4 lg:pl-6">
            <h4 className="text-sm font-semibold text-white uppercase tracking-wider">Access</h4>
            <ul className="flex flex-col gap-3.5">
              <li className="flex items-start gap-3 text-sm text-slate-400">
                <ShieldCheck className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
                <span>Accounts are provisioned by your college. No public self-registration.</span>
              </li>
              <li className="flex items-start gap-3 text-sm text-slate-400">
                <BookOpen className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
                <span>Protected records strictly isolated to the authenticated student.</span>
              </li>
              <li>
                <a
                  href="/login.html"
                  className="group flex items-center gap-2.5 text-sm text-blue-400 hover:text-blue-300 transition-colors duration-200 font-medium"
                >
                  <Mail className="w-4 h-4" />
                  Sign in with college email →
                </a>
              </li>
            </ul>
          </div>
        </div>

        {/* Divider */}
        <div className="w-full h-px bg-slate-800/80 mb-6" />

        {/* Bottom Bar */}
        <div className="flex flex-col md:flex-row justify-between items-center gap-4">
          <div className="text-xs text-slate-400">
            © {currentYear} Campus Companion. All rights reserved.
          </div>

          <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-xs text-slate-400">
            <span>A student management &amp; learning platform</span>
            <span className="hidden sm:inline text-slate-700">·</span>
            <a href="/privacy.html" className="hover:text-blue-400 transition-colors duration-200">
              Privacy Policy
            </a>
            <span className="hidden sm:inline text-slate-700">·</span>
            <a href="/terms.html" className="hover:text-blue-400 transition-colors duration-200">
              Terms of Use
            </a>
            <span className="hidden sm:inline text-slate-700">·</span>
            <a href="/login.html" className="hover:text-blue-400 transition-colors duration-200">
              Log in
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
