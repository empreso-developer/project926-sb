// app/jobs/page.tsx
'use client';
import { Globe, Clock, DollarSign, Briefcase, Star } from 'lucide-react';
import {Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useState } from 'react';
import { VisuallyHidden } from "@radix-ui/react-visually-hidden";
import MarkdownIt from 'markdown-it';
import Link from 'next/link';





const mdParser = new MarkdownIt(
  {
    html: true,
    linkify: true,
    typographer: true,
    breaks: true  // Treats single newlines as <br>
  }
);


// import ReactMarkdown from 'react-markdown';
// import remarkGfm from 'remark-gfm';
// import rehypeHighlight from 'rehype-highlight';


interface JobCardProps {
  id: number;
  title: string;
  description: string;
  companyName: string;  // camelCase instead of "company Name"
  companyLogo: string;
  location?: string | null;
  jobType: string;      // Or enum if you have specific types
  salaryRange?: string | null;
  postedAt: Date;
  expiryDate?: Date | null;
  status: string;       // Or enum for status values
}



const JobModal = ({
  job,
  isOpen,
  onClose,
}: {
  job: JobCardProps | null;
  isOpen: boolean;
  onClose: () => void;
}) => {
  // Return nothing if no job is provided
  if (!job) return null;

  // Parse the markdown string to HTML
  const parsedMarkdown = mdParser.render(job.description);

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="sm:max-w-3xl bg-transparent backdrop-blur-md border-0 rounded-2xl">
        <div className=" relative rounded-2xl bg-black sm:bg-black/30 backdrop-blur-md p-6 sm:p-8">
          <DialogHeader>
            <DialogTitle>
              <VisuallyHidden>{job.title} at {job.companyName}</VisuallyHidden>
            </DialogTitle>

            <div className="flex items-center justify-between gap-4 mb-6">
              <div>
                <h2 className="text-2xl font-bold text-gray-100">{job.title}</h2>
                <p className="text-lg text-emerald-400">{job.companyName}</p>
              </div>
              {/* <img 
                src={job.companyLogo} 
                alt={job.companyName}
                className="hidden sm:block w-20 h-20 object-contain rounded-lg"
              /> */}
            </div>
          </DialogHeader>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 mb-6">
            <div className="flex items-center gap-2">
              <Globe className="h-5 w-5 text-emerald-500" />
              <span className="text-gray-300">{job.location || 'Remote'}</span>
            </div>
            <div className="flex items-center gap-2">
              <Briefcase className="h-5 w-5 text-cyan-500" />
              <span className="text-gray-300">{job.jobType.replace('_', ' ')}</span>
            </div>
            {job.salaryRange && (
              <div className="flex items-center gap-2">
                <DollarSign className="h-5 w-5 text-purple-500" />
                <span className="text-gray-300">{job.salaryRange}</span>
              </div>
            )}
            <div className="flex items-center gap-2">
              <Clock className="h-5 w-5 text-amber-500" />
              <span className="text-gray-300">
                Posted {new Date(job.postedAt).toLocaleDateString('en-US', { 
                  month: 'short', 
                  day: 'numeric' 
                })}
              </span>
            </div>
          </div>

          <div 
            className="prose prose-invert max-w-none mb-8 
            max-h-[35dvh]  /* Reduced mobile height */
            sm:max-h-[350px] /* Original desktop height */
            overflow-y-auto"
            dangerouslySetInnerHTML={{ __html: parsedMarkdown }}
            
          >
          </div>

          <div className="flex flex-col sm:flex-row gap-4">
            <button
              onClick={onClose}
              className="flex-1 px-6 py-3 rounded-lg bg-gray-800 text-gray-300 hover:bg-gray-700 transition"
            >
              Close
            </button>
            <Link href={`jobs/verify-details/${job.id}`} className="text-center flex-1 px-6 py-3 rounded-lg bg-gradient-to-r from-emerald-600 to-cyan-600 text-white hover:opacity-90 transition">
              Submit Application
            </Link>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};








const JobsPage =  ( {jobs} : {jobs: JobCardProps[]}) => {
  // Using demo data from search results
  const [selectedJob, setSelectedJob] = useState<JobCardProps | null>(null);




  
  
  return (
    <div className="min-h-screen bg-black text-gray-100">
      <header className="relative h-56 overflow-hidden sm:h-72 md:h-96">
        <div className="relative z-10 flex h-full flex-col items-center justify-center px-4 text-center">
          <h1 className="text-3xl font-bold tracking-tighter text-white drop-shadow-lg sm:text-4xl md:text-5xl">
            Find Your Next Chapter
          </h1>
          <p className="mt-2 text-base text-gray-300 sm:mt-3 sm:text-lg">
            {jobs.length} transformative opportunities waiting
          </p>
        </div>
      </header>

      <div className="mx-auto max-w-7xl px-3 py-8 sm:px-4 sm:py-12 lg:px-6">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {jobs.map((job) => (
            <article 
              key={job.id}
              className="group relative flex min-w-[280px] flex-col rounded-2xl border border-gray-800 bg-black p-4 transition-all hover:bg-gray-900/80 sm:p-6"
            >
              <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-emerald-500/10 to-cyan-500/10 opacity-0 transition-opacity group-hover:opacity-100" />
              
              <div className="flex flex-col justify-between h-full relative space-y-3 sm:space-y-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    {/* <img 
                      src={job.companyLogo} 
                      alt={job.companyName}
                      className="hidden sm:block sm:w-24 object-contain shrink-0"
                    /> */}
                    <div className="sm:hidden">
                      <h2 className="text-base font-semibold text-gray-100 line-clamp-2">{job.title}</h2>
                      <p className="text-xs text-emerald-400 truncate">{job.companyName}</p>
                    </div>
                  </div>
                  <span className="inline-flex items-center rounded-full bg-emerald-900/20 px-2 py-0.5 text-[0.7rem] sm:px-2.5 sm:py-1 sm:text-xs text-emerald-400">
                    <Star className="mr-1 h-3 w-3 sm:mr-1.5 sm:h-3.5 sm:w-3.5" />
                    Featured
                  </span>
                </div>

                <div className="hidden sm:block">
                  <h2 className="text-lg font-semibold text-gray-100 line-clamp-2 md:text-xl">{job.title}</h2>
                  <p className="mt-0.5 text-sm text-emerald-400 truncate">{job.companyName}</p>
                </div>

                <div className="grid grid-cols-1 gap-2 text-xs sm:grid-cols-2 sm:gap-3 sm:text-sm">
                  <div className="flex items-center gap-1.5">
                    <Globe className="h-3.5 w-3.5 text-emerald-500 sm:h-4 sm:w-4" />
                    <span className="truncate">{job.location || 'Remote'}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Briefcase className="h-3.5 w-3.5 text-cyan-500 sm:h-4 sm:w-4" />
                    <span className="truncate">{job.jobType.replace('_', ' ')}</span>
                  </div>
                  {job.salaryRange && (
                    <div className="flex items-center gap-1.5">
                      <DollarSign className="h-3.5 w-3.5 text-purple-500 sm:h-4 sm:w-4" />
                      <span className="truncate">{job.salaryRange}</span>
                    </div>
                  )}
                  <div className="flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5 text-amber-500 sm:h-4 sm:w-4" />
                    <span className="truncate">
                      {new Date(job.postedAt).toLocaleDateString('en-US', {
                        month: 'short',
                        day: 'numeric'
                      })}
                    </span>
                  </div>
                </div>
                <div>
                <button 
                  onClick={() => setSelectedJob(job)}
                  className="w-full transform rounded-lg bg-gradient-to-r from-emerald-600 to-cyan-600 px-3 py-2 text-xs font-medium text-white transition hover:scale-[1.02] hover:shadow-lg sm:px-4 sm:py-2.5 sm:text-sm"
                >
                  Apply Now
                </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      </div>

      <JobModal 
        job={selectedJob}
        isOpen={!!selectedJob}
        onClose={() => setSelectedJob(null)}
      />
    </div>

    
  );
  
};

export default JobsPage;