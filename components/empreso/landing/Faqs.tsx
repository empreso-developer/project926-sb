"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";
import { Card } from "../ui/Card";

interface FAQItemProps {
    question: string;
    answer: string;
    index: number;
    isOpen: boolean;
    onToggle: (index: number) => void;
}

function FAQItem({ question, answer, index, isOpen, onToggle }: FAQItemProps) {
    const formattedAnswer = answer.split("\n").map((item, idx) => (
        <span key={idx}>
            {item}
            <br />
        </span>
    ));

    return (
        <Card className="p-0 transition hover:bg-card/70">
            <button
                onClick={() => onToggle(index)}
                className="w-full flex items-center justify-between px-6 py-5 text-left"
            >
                <h3 className="text-sm font-medium text-foreground">
                    {question}
                </h3>

                <motion.div
                    animate={{ rotate: isOpen ? 180 : 0 }}
                    transition={{ duration: 0.25 }}
                    className="text-muted-foreground"
                >
                    <ChevronDown className="h-4 w-4" />
                </motion.div>
            </button>

            <AnimatePresence>
                {isOpen && (
                    <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: "auto", opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.25 }}
                    >
                        <div className="border-t border-border/60 px-6 pb-5 pt-4 text-sm text-muted-foreground leading-relaxed">
                            {formattedAnswer}
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </Card>
    );
}

const faqs = [
    {
        question: "What makes EMPRESO different from other job platforms?",
        answer: "We don’t just help you apply for jobs—we optimize your resume, market your profile, match you with jobs, and provide direct employer connections for faster hiring.",
    },
    {
        question: "How does AI help in my job search?",
        answer: "✔ Optimizes your resume for ATS\n✔ Matches you with high-response job listings\n✔ Provides mock interview feedback\nGet more interview calls & higher job visibility!",
    },
    {
        question: "How does EMPRESO improve my resume and LinkedIn profile?",
        answer: "✔ ATS-friendly resume formatting\n✔ Keyword optimization for better ranking\n✔ LinkedIn profile makeover for 3X recruiter views",
    },
    {
        question: "Do you apply to jobs on my behalf?",
        answer: "Yes! We auto-submit applications to top job portals & exclusive vendor networks to increase your chances of getting hired.",
    },
    {
        question: "How does your interview coaching work?",
        answer: "✔ 1-on-1 coaching with experts\n✔ AI-powered mock interviews\n✔ Behavioral & technical interview prep",
    },
    {
        question: "How does EMPRESO’s background verification service help?",
        answer: "✔ Faster background checks for IT, banking & security jobs\n✔ Ensures compliance & speeds up onboarding",
    },
    {
        question: "What training programs do you offer?",
        answer: "Full-Stack, Cloud, AI, Data Science, Cybersecurity & DevOps\nIncludes real-world projects & job placement support!",
    },
    {
        question: "Do you guarantee job placement?",
        answer: "We have a 98% success rate, with most candidates landing jobs within 90 days.",
    },
    {
        question: "How much does EMPRESO’s service cost?",
        answer: "We offer flexible plans for resume boosting, job marketing, and full career acceleration.\nSign up for a free consultation to find the best option for you!",
    },
    {
        question: "How long does it take to see results?",
        answer: "✔ Interview calls within 2-3 weeks\n✔ Job placement within 3 months\n💡 Faster than traditional applications!",
    },
    {
        question: "Do I need experience to use EMPRESO?",
        answer: "No! We help fresh graduates, career switchers & experienced professionals find the right job.",
    },
    {
        question: "How do I get started?",
        answer: " Sign up for a free consultation\nChoose a service (resume, job marketing, interview coaching, or full package)\n Let EMPRESO fast-track your job search!",
    },
];

function Faq02() {
    const [openIndex, setOpenIndex] = useState<number | null>(null);

    const handleToggle = (index: number) => {
        setOpenIndex((prev) => (prev === index ? null : index));
    };

    return (
        <section className="relative mx-auto max-w-7xl px-6 py-24">
            <div className="max-w-3xl">
                <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
                    FAQs
                </p>
                <h2 className="mt-6 text-4xl font-mono tracking-tight sm:text-5xl">
                    Frequently Asked Questions
                </h2>
                <p className="mt-5 font-mono text-base text-muted-foreground">
                    Everything you need to know about our platform
                </p>
            </div>

            <div className="mt-16 grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="flex flex-col gap-6">
                    {faqs
                    .map((faq, i) => ({ ...faq, originalIndex: i }))
                    .filter((_, i) => i % 2 === 0)
                    .map((faq) => (
                        <FAQItem
                        key={faq.originalIndex}
                        index={faq.originalIndex}
                        isOpen={openIndex === faq.originalIndex}
                        onToggle={handleToggle}
                        {...faq}
                        />
                    ))}
                </div>

                <div className="flex flex-col gap-6">
                    {faqs
                    .map((faq, i) => ({ ...faq, originalIndex: i }))
                    .filter((_, i) => i % 2 === 1)
                    .map((faq) => (
                        <FAQItem
                        key={faq.originalIndex}
                        index={faq.originalIndex}
                        isOpen={openIndex === faq.originalIndex}
                        onToggle={handleToggle}
                        {...faq}
                        />
                    ))}
                </div>
            </div>
        </section>
    );
}

export default Faq02;
