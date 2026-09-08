'use client'
import { ArrowUpRight, MapPin, Calendar, Globe, Clock, DollarSign, Briefcase, Star } from 'lucide-react';
import { Card } from "@/components/empreso/ui/Card";
import {Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import Link from "next/link";
import { VisuallyHidden } from "@radix-ui/react-visually-hidden";
import MarkdownIt from 'markdown-it';
import { useState } from 'react';
import MarkdownRenderer from '../MarkdownRenderer';

type Job = {
  id: number;
  companyName: string;
  title: string;
  description: string;
  jobType: string;
  location?: string | null;
  salaryRange?: string | null;
  postedAt: Date;
};


const formatDate = (date: Date) => {
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(date));
};

export const Careers = ({ jobs }: { jobs: Job[] }) => {

  const [selectedJob, setSelectedJob] = useState<Job | null>(null);

  return (
    <>
      <section className="relative border-t border-white/[0.1] bg-neutral-950">
        <div className="mx-auto max-w-7xl px-6 py-5">
          <div className="mt-14 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {jobs.map((j) => (
              <Card key={j.id} className="flex flex-col p-6">
                <h3 className="text-lg font-semibold">{j.title}</h3>

                {/* Job type */}
                <div className='flex flex-wrap justify-between mt-2 text-xs uppercase tracking-wider text-muted-foreground'>
                  <b>
                    {j.companyName}
                  </b>
                  <p>
                    {j.jobType}
                  </p>
                </div>

                {/* Meta info */}
                <div className="mt-4 space-y-2 text-xs text-muted-foreground">
                  {j.location && (
                    <div className="flex items-center gap-2">
                      <MapPin className="h-3.5 w-3.5" />
                      {j.location}
                    </div>
                  )}

                  <div className="flex items-center gap-2">
                    <Calendar className="h-3.5 w-3.5" />
                    Posted {formatDate(j.postedAt)}
                  </div>

                  {j.salaryRange && (
                    <span className="mt-2 inline-block text-xs font-medium text-green-400">
                      {j.salaryRange}
                    </span>
                  )}
                </div>

                {/* CTA */}
                <button
                  onClick={() => setSelectedJob(j)}
                  className="mt-6 inline-flex w-fit items-center gap-2 rounded-pill border border-border bg-background/60 px-5 py-2.5 text-sm font-medium transition-colors hover:bg-card"
                  >
                  Apply now <ArrowUpRight className="h-4 w-4" />
                </button>
              </Card>
            ))}
          </div>
        </div>
      </section>
      <JobModal 
        job={selectedJob}
        isOpen={!!selectedJob}
        onClose={() => setSelectedJob(null)}
      />
    </>
  )
}


export const JobModal = ({
  job,
  isOpen,
  onClose,
}: {
  job: Job | null;
  isOpen: boolean;
  onClose: () => void;
}) => {
  if (!job) return null;

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl border border-border/60 bg-card/80 backdrop-blur-xl rounded-2xl p-0 overflow-hidden">

        {/* Header */}
        <DialogHeader className="p-6 pb-4 border-b border-border/50">
          <DialogTitle>
            <VisuallyHidden>{job.title}</VisuallyHidden>
          </DialogTitle>

          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-2xl font-semibold tracking-tight">
                {job.title}
              </h2>
              <p className="text-sm text-muted-foreground mt-1">
                {job.companyName}
              </p>
            </div>
          </div>
        </DialogHeader>

        {/* Meta Info */}
        <div className="px-6 py-4 grid grid-cols-2 gap-3 text-sm text-muted-foreground">
          
          {job.location && (
            <div className="flex items-center gap-2">
              <MapPin className="h-4 w-4" />
              {job.location}
            </div>
          )}

          <div className="flex items-center gap-2">
            <Briefcase className="h-4 w-4" />
            {job.jobType.replace('_', ' ')}
          </div>

          <div className="flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            Posted {formatDate(job.postedAt)}
          </div>

          {job.salaryRange && (
            <div className="flex items-center gap-2 text-green-400 font-medium">
              {job.salaryRange}
            </div>
          )}
        </div>

        {/* Description */}
        <div className="px-6 pb-6 max-h-[40vh] overflow-y-auto text-sm sm:text-base">
          <MarkdownRenderer
            markdown={job.description}
          />
        </div>

        {/* Footer CTA */}
        <div className="flex flex-col sm:flex-row gap-3 p-6 pt-4 border-t border-border/50">
          
          <button
            onClick={onClose}
            className="flex-1 rounded-lg border border-border bg-background/60 px-4 py-2.5 text-sm hover:bg-card transition"
          >
            Close
          </button>

          <Link
            href={`jobs/verify-details/${job.id}`}
            className="flex-1 inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:opacity-90 transition"
          >
            Apply Now <ArrowUpRight className="h-4 w-4" />
          </Link>
        </div>

      </DialogContent>
    </Dialog>
  );
};
