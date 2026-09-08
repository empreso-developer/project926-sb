/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
'use client';
import React, { useState } from 'react';
import { FiUploadCloud, FiCpu, FiTarget, FiCheckCircle, FiAward, FiFileText, FiCopy, FiBriefcase, FiList, FiEdit, FiExternalLink } from 'react-icons/fi';
import { 
  FaFileAlt, FaLightbulb, 
  FaExclamationTriangle, FaTimes, FaHeading, FaBriefcase,
  FaGraduationCap, FaLayerGroup, FaVolumeUp, FaGlobe 
} from 'react-icons/fa';
import { Calendar } from 'lucide-react';
// import { fetchATSReportStream } from '@/app/api/openai';
import MarkdownRenderer from './MarkdownRenderer';



import MarkdownIt from 'markdown-it';

const md = new MarkdownIt();

const copyHTML = (markdown: string) => {
  const parsedHTML = md.render(markdown);
  
  const blob = new Blob([parsedHTML], { type: "text/html" });
  const data = [new ClipboardItem({ "text/html": blob })];

  navigator.clipboard.write(data).then(() => {
    console.log("Formatted content copied!");
  }).catch(err => {
    console.error("Failed to copy", err);
  });
};




//ai stuff





export type ATSReport = {
  ATS: string;
  ATS_tips: string[];
  missing_skills: string[];
  Summary: string;
  section_heading: string;
  job_title_match: string;
  education: string;
  date_format: string;
  job_level_match: string;
  resume_tone: string;
  web_presence: string;
  improved_skills_section: string;
  improved_experience_section: string;
  improved_summary_section: string;
}


const AIResumeOptimizer: React.FC = () => {
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [jobDescription, setJobDescription] = useState<string>('');
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [resumeText, setResumeText] = useState<string>('');
  const [copied, setCopied] = useState<string>('none');

  const [report, setReport] = useState<ATSReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);

    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setResumeFile(e.dataTransfer.files[0]);
      e.dataTransfer.clearData();
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      setResumeFile(file);
      console.log("File Selected : ", file.name);
    }
  };
  
  const [streamProgress, setStreamProgress] = useState<number>(0);
  const [isStreaming, setIsStreaming] = useState<boolean>(false);
  
  const handleSubmit = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setIsProcessing(true);
    setIsStreaming(true);
    setError(null);
    setStreamProgress(0);
    
    try {
      // Validate inputs
      if (!resumeFile) {
        throw new Error("Please upload a resume file.");
      }
      
      if (!jobDescription.trim()) {
        throw new Error("Please provide a job description.");
      }
      
      console.log('Starting streaming analysis...');
      
      // Create a loading placeholder for the report
      setReport({
        ATS: "Analyzing...",
        ATS_tips: ["Processing your resume..."],
        missing_skills: ["Identifying missing skills..."],
        Summary: "Analyzing summary...",
        section_heading: "Checking headings...",
        job_title_match: "Analyzing job title match...",
        education: "Evaluating education...",
        date_format: "Checking date formats...",
        job_level_match: "Analyzing experience level...",
        resume_tone: "Analyzing tone...",
        web_presence: "Checking web presence...",
        improved_summary_section: "Generating improved summary...",
        improved_experience_section: "Generating improved experience section...",
        improved_skills_section: "Generating improved skills section..."
      } as ATSReport);
      

      const formData = new FormData();
      formData.append("file", resumeFile);
      formData.append("jobDescription", jobDescription);

      // Use fetch to call our API route with streaming
      const response = await fetch(`/api/ats-stream`, {
        method: 'POST',
        body: formData,
      });
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || "API request failed");
      }
      
      // Set up stream processing
      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error("Failed to get stream reader");
      }
      
      const decoder = new TextDecoder();
      let buffer = "";
      let jsonString = '';
      
      // Process the stream chunks
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value);

        const parts = buffer.split("\n\n");
        buffer = parts.pop() || "";

        for (const part of parts) {
          if (!part.startsWith("data: ")) continue;

          try {
            const data = JSON.parse(part.replace("data: ", ""));

            if (data.error) throw new Error(data.error);

            if (data.content) {
              jsonString += data.content;
              setStreamProgress(data.length || 0);

              try {
                const partial = JSON.parse(jsonString);
                setReport(partial);
              } catch {
                // ignore partial JSON errors
              }
            }

            if (data.complete) {
              const final = JSON.parse(jsonString);
              setReport(final);
              setResumeFile(null);
              setJobDescription("");
              console.log("ATS Report complete");
            }
          } catch {
            // ignore bad chunks
          }
        }
      }
    } catch (err) {
      console.error("Error generating ATS report:", err);
      setError(err instanceof Error ? err.message : "Unknown error");
    } finally {
      setIsProcessing(false);
      setIsStreaming(false);
    }
  };
  
  return (
    <div className="min-h-screen text-gray-100">
      <div className="mx-auto max-w-7xl px-3 py-8 sm:px-4 sm:py-12 lg:px-6">
        <div className="flex flex-col items-center justify-center">
          {/* Header with animated elements */}
          <div className="text-center mb-16 relative">
            <div className="absolute -top-20 left-1/2 transform -translate-x-1/2 w-64 h-64 bg-gradient-to-r from-emerald-600/20 to-cyan-600/20 rounded-full filter blur-3xl opacity-30 animate-pulse" />
            
            <h1 className="text-3xl font-mono-display tracking-tighter text-white drop-shadow-lg sm:text-4xl md:text-5xl relative">
              <span className="inline-block relative">
                {/* <span className="absolute -inset-1 w-full h-full bg-gradient-to-r from-emerald-400 to-cyan-400 opacity-50 blur-lg rounded-lg"></span> */}
                <span className="relative bg-gradient-to-r from-emerald-400 to-cyan-400 bg-clip-text text-transparent">
                  AI-Powered
                </span>
              </span>{" "}
              Resume Optimization
            </h1>
            
            <p className="mt-4 text-lg font-mono text-gray-300 max-w-3xl mx-auto">
              Elevate your job applications with intelligent resume tailoring that matches exactly what employers are looking for
            </p>
            
            <div className="flex flex-wrap justify-center mt-6 space-x-4">
              <div className="flex items-center">
                <div className="w-2 h-2 bg-emerald-400 rounded-full mr-2"></div>
                <span className="text-sm text-gray-400">Match Keywords</span>
              </div>
              <div className="flex items-center">
                <div className="w-2 h-2 bg-emerald-400 rounded-full mr-2"></div>
                <span className="text-sm text-gray-400">Pass ATS Screening</span>
              </div>
              <div className="flex items-center">
                <div className="w-2 h-2 bg-emerald-400 rounded-full mr-2"></div>
                <span className="text-sm text-gray-400">Land More Interviews</span>
              </div>
            </div>
          </div>

          {/* Process Steps */}
          <div className="relative w-full max-w-4xl h-64 md:h-auto md:flex md:justify-between md:items-center mb-12">
  {[
    { number: "01", text: "Upload Resume" },
    { number: "02", text: "Paste Job Description" },
    { number: "03", text: "Get Optimized Results" }
  ].map((step, index) => {
    // For mobile devices, set absolute positioning to form a triangle:
    let mobilePosition = "";
    if (index === 0) {
      // Top center
      mobilePosition = "absolute top-0 left-1/2 transform -translate-x-1/2";
    } else if (index === 1) {
      // Bottom left
      mobilePosition = "absolute bottom-0 left-0";
    } else if (index === 2) {
      // Bottom right
      mobilePosition = "absolute bottom-0 right-0";
    }

    return (
      <div
        key={index}
        className={`flex flex-col items-center md:static ${mobilePosition}`}
      >
        <div className="w-12 h-12 rounded-full bg-gradient-to-r from-emerald-600 to-cyan-600 flex items-center justify-center text-white font-bold">
          {step.number}
        </div>
        <p className="mt-2 text-gray-300 text-center">{step.text}</p>
      </div>
    );
  })}
</div>


          {/* Main content */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 w-full max-w-6xl">
            {/* Resume Upload Section */}
            <div 
              className={`group relative flex flex-col rounded-2xl border-[4px] border-gray-800 border-dashed bg-black p-6 transition-all hover:bg-gray-900/80 ${isDragging ? 'border-emerald-500 ' : ''}`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
            >
              <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-emerald-500/10 to-cyan-500/10 opacity-0 transition-opacity group-hover:opacity-100" />
              
              <div className="relative space-y-6 flex flex-col items-center justify-center min-h-[250px]">
                <div className={`w-20 h-20 rounded-full flex items-center justify-center ${resumeFile ? 'bg-emerald-500/20' : 'bg-gray-800/50'}`}>
                  <FiUploadCloud className={`w-10 h-10 ${resumeFile ? 'text-emerald-400' : 'text-gray-400'}`} />
                </div>
                
                <h3 className="text-center text-xl font-semibold text-white">
                  {resumeFile ? 'Resume Uploaded' : 'Upload Your Resume'}
                </h3>
                
                {resumeFile ? (
                  <div className="flex justify-between items-center space-x-2 bg-gray-800/50 rounded-lg px-4 py-3 w-full max-w-md border border-emerald-500/30">
                    <div className="text-emerald-400">
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-gray-200 truncate">{resumeFile.name}</span>
                    <button 
                      onClick={() => setResumeFile(null)}
                      className="ml-auto text-gray-400 hover:text-white"
                    >
                      ✕
                    </button>
                  </div>
                ) : (
                  <div className="text-center">
                    <p className="text-gray-400 mb-4">
                      Drag & drop your resume here or click to browse
                    </p>
                    <label className="cursor-pointer">
                      <span className="transform rounded-full bg-gradient-to-r from-emerald-600 to-cyan-600 px-4 py-2.5 text-sm font-medium text-white transition hover:scale-[1.02] hover:shadow-lg inline-block">
                        Select Resume
                      </span>
                      <input 
                        type="file" 
                        className="hidden" 
                        accept=".pdf,.doc,.docx" 
                        onChange={handleFileChange}
                      />
                    </label>
                  </div>
                )}
                
                <p className="text-xs text-gray-500">
                  Supported formats: PDF, DOCX (Max 5MB)
                </p>
              </div>
            </div>

            {/* Job Description Section */}
            <div className="group relative flex flex-col rounded-2xl border-[4px] border-gray-800 border-dashed bg-black p-6 transition-all hover:bg-gray-900/80">
              <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-emerald-500/10 to-cyan-500/10 opacity-0 transition-opacity group-hover:opacity-100" />
              
              <div className="relative space-y-4 flex flex-col h-full">
                <h3 className="text-center text-xl font-semibold text-white mb-4">
                  Paste Job Description
                </h3>
                
                <textarea 
                  className="flex-1 min-h-[200px] bg-gray-900/60 border border-gray-700 rounded-lg p-4 text-gray-200 placeholder-gray-500 focus:ring-2 focus:ring-emerald-500 focus:border-transparent resize-none"
                  placeholder="Copy and paste the job description here to help our AI understand the requirements..."
                  value={jobDescription}
                  onChange={(e) => setJobDescription(e.target.value)}
                />
                
                <div className="flex justify-between items-center text-xs text-gray-500">
                  <span>Pro tip: Include the full job posting for best results</span>
                  <span>{jobDescription.length} characters</span>
                </div>
              </div>
            </div>
          </div>





          {/* Display error message */}
          {error && (
            <div className="mt-6 text-center text-red-500">
              <p>{error}</p>
            </div>
          )}
          {isStreaming && (
            <div className="mt-3 mb-6">
              <div className="w-full bg-gray-800 rounded-full h-2.5">
                <div 
                  className="bg-gradient-to-r from-emerald-400 to-cyan-500 h-2.5 rounded-full"
                  style={{ width: `${Math.min((streamProgress / 10000) * 100, 100)}%` }}
                ></div>
              </div>
              <p className="text-xs text-gray-400 mt-1 text-right">
                Processing ATS analysis...
              </p>
            </div>
          )}



          {/* Submit Button */}
          <div className="mt-12 text-center flex flex-wrap justify-center gap-4">
          <button 
            className="relative group px-8 py-4 bg-[#0B1020]/95 rounded-2xl text-white text-lg transition transform hover:scale-[1.02] hover:shadow-lg disabled:opacity-70 disabled:cursor-not-allowed border border-white/10"
            disabled={!resumeFile || !jobDescription || isProcessing}
            onClick={handleSubmit}
          >
            <span className="flex items-center justify-center gap-2">
              {isProcessing ? (
                <>
                  <svg className="animate-spin -ml-1 mr-2 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  <span>Optimizing Resume...</span>
                </>
              ) : (
                <>
                  <FiCpu className="w-5 h-5" />
                  <span>Optimize My Resume</span>
                </>
              )}
            </span>
          </button>
          
          {/* Empreso AI Button */}
          <a
            href="https://ai.empreso.in"
            target="_blank"
            rel="noopener noreferrer"
            className="group relative inline-flex items-center justify-center overflow-hidden rounded-2xl p-[1.5px] transition-all duration-300 hover:scale-[1.03]"
          >
            {/* Glow */}
            <span className="absolute inset-0 rounded-2xl blur-xl bg-cyan-400/30 opacity-0 transition-opacity duration-500 group-hover:opacity-100" />

            {/* Main Button */}
            <span className="relative z-10 flex items-center gap-3 rounded-2xl bg-[#0B1020]/95 px-8 py-4 backdrop-blur-xl border border-white/10">
              <FiExternalLink className="h-5 w-5 text-cyan-300 transition-transform duration-300 group-hover:rotate-12" />

              <span className="bg-gradient-to-r from-cyan-300 via-blue-400 to-purple-400 bg-clip-text text-lg font-semibold text-transparent tracking-wide">
                Chat With Empreso AI
              </span>

              {/* AI Shine Effect */}
              <span className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/10 to-transparent transition-transform duration-1000 group-hover:translate-x-full" />
            </span>
          </a>
        </div>

        <p className="mt-4 text-sm text-gray-400">
          Our AI will analyze both documents and create a tailored resume in seconds
        </p>



          {/* Display ATS Report if available */}
          {report && (
  <div className="mt-12 p-6  rounded-2xl border-[4px] border-gray-800 border-dashed w-full max-w-6xl mx-auto">
    <div className="flex items-center mb-6">
      <FiCpu className="w-6 h-6 text-emerald-400 mr-3" />
      <h2 className="text-2xl font-bold text-emerald-400">ATS Report</h2>
    </div>
    
    {/* ATS Score Section */}
    <div className="mb-6 p-5 border-2 border-dashed border-gray-700 rounded-lg ">
      <div className="flex items-center justify-between">
        <div className="flex items-center">
          <FiTarget className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">ATS Score</h3>
        </div>
        <div className="text-3xl font-bold text-emerald-400">{report.ATS}</div>
      </div>
    </div>
    
    {/* ATS Tips Section */}
    <div className="mb-6 p-5 border-2 border-dashed border-gray-700 rounded-lg ">
      <div className="flex items-center mb-3">
        <FiCheckCircle className="w-5 h-5 text-emerald-400 mr-2" />
        <h3 className="text-xl font-semibold text-white">ATS Tips</h3>
      </div>
      <ul className="space-y-2">
        {report.ATS_tips.map((tip, idx) => (
          <li key={idx} className="flex items-start">
            <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-emerald-500/20 mr-2 mt-0.5">
              <FaLightbulb className="w-3 h-3 text-emerald-400" />
            </span>
            <span className="text-left text-gray-300">{tip}</span>
          </li>
        ))}
      </ul>
    </div>
    
    {/* Missing Skills Section */}
    <div className="mb-6 p-5 border-2 border-dashed border-gray-700 rounded-lg ">
      <div className="flex items-center mb-3">
        <FaExclamationTriangle className="w-5 h-5 text-amber-400 mr-2" />
        <h3 className="text-xl font-semibold text-white">Missing Skills</h3>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
        {report.missing_skills.map((skill, idx) => (
          <div key={idx} className="flex items-center p-2 bg-gray-800/50 rounded">
            <FaTimes className="w-4 h-4 text-red-400 mr-2" />
            <span className="text-gray-300">{skill}</span>
          </div>
        ))}
      </div>
    </div>
    
    {/* Other Report Sections */}
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      {/* Summary Section */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <FaFileAlt className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className=" text-xl font-semibold text-white">Summary</h3>
        </div>
        <p className="text-left text-gray-300">{report.Summary}</p>
      </div>
      
      {/* Section Headings */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <FaHeading className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">Section Headings</h3>
        </div>
        <p className="text-left text-gray-300">{report.section_heading}</p>
      </div>
      
      {/* Job Title Match */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <FaBriefcase className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">Job Title Match</h3>
        </div>
        <p className="text-left text-gray-300">{report.job_title_match}</p>
      </div>
      
      {/* Education */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <FaGraduationCap className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">Education</h3>
        </div>
        <p className="text-left text-gray-300">{report.education}</p>
      </div>
      
      {/* Job Level Match */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <FaLayerGroup className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">Job Level Match</h3>
        </div>
        <p className="text-left text-gray-300">{report.job_level_match}</p>
      </div>
      
      {/* Resume Tone */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <FaVolumeUp className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">Resume Tone</h3>
        </div>
        <p className="text-left text-gray-300">{report.resume_tone}</p>
      </div>
      
      {/* Web Presence */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <FaGlobe className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">Web Presence</h3>
        </div>
        <p className="text-left text-gray-300">{report.web_presence}</p>
      </div>

      {/* Web Presence */}
      <div className="p-5 border-2 border-dashed border-gray-700 rounded-lg ">
        <div className="flex items-center mb-3">
          <Calendar className="w-5 h-5 text-emerald-400 mr-2" />
          <h3 className="text-xl font-semibold text-white">Date Format</h3>
        </div>
        <p className="text-left text-gray-300">{report.date_format}</p>
      </div>


    </div>
      <div className="mt-6">
        <h3 className="text-xl font-semibold text-white mb-4 flex items-center">
          <FiEdit className="w-5 h-5 text-emerald-400 mr-2" />
          Improved Resume Sections
        </h3>
  
  {/* Improved Skills Section */}
        <div className="mb-6 p-5 border-2 border-dashed border-gray-700 rounded-lg">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center">
              <FiList className="w-5 h-5 text-emerald-400 mr-2" />
              <h3 className="text-xl font-semibold text-white">Improved Skills Section</h3>
            </div>
            <button 
              onClick={() => {copyHTML(report.improved_skills_section)
                setCopied('skills')
                setTimeout(() => setCopied('none'), 2000)
              }}

              className="p-2 rounded-full hover:bg-gray-700 transition-colors"
              title="Copy to clipboard"
            >
            {copied === 'skills' ? (
              <FiCheckCircle className="w-5 h-5 text-emerald-400" />

            ) : (
                <FiCopy className="w-5 h-5 text-emerald-400" />

            )}              </button>
          </div>
          <div className=" p-4 rounded-lg text-gray-300">
          <MarkdownRenderer markdown={report.improved_skills_section} />
          </div>
        </div>
        
        {/* Improved Experience Section */}
        <div className="mb-6 p-5 border-2 border-dashed border-gray-700 rounded-lg">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center">
              <FiBriefcase className="w-5 h-5 text-emerald-400 mr-2" />
              <h3 className="text-xl font-semibold text-white">Improved Experience Section</h3>
            </div>
            <button 
              onClick={() =>  {
                copyHTML(report.improved_experience_section)
                setCopied('experience')
                setTimeout(() => setCopied('none'), 2000)
              }}
              className="p-2 rounded-full hover:bg-gray-700 transition-colors"
              title="Copy to clipboard"
            >
            {copied === 'experience' ? (
              <FiCheckCircle className="w-5 h-5 text-emerald-400" />

            ) : (
                <FiCopy className="w-5 h-5 text-emerald-400" />

            )}            
            </button>
          </div>
          <div className=" p-4 rounded-lg text-gray-300">
          <MarkdownRenderer markdown={report.improved_experience_section} />
          </div>
        </div>
        
        {/* Improved Summary Section */}
        <div className="mb-6 p-5 border-2 border-dashed border-gray-700 rounded-lg">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center">
              <FiFileText className="w-5 h-5 text-emerald-400 mr-2" />
              <h3 className="text-xl font-semibold text-white">Improved Summary Section</h3>
            </div>
            <button 
              onClick={() =>  {copyHTML(report.improved_summary_section)
                setCopied('summary')
                setTimeout(() => setCopied('none'), 2000)
              }}

              className="p-2 rounded-full hover:bg-gray-700 transition-colors"
              title="Copy to clipboard"
            >

            {copied === 'summary' ? (
              <FiCheckCircle className="w-5 h-5 text-emerald-400" />

            ) : (
                <FiCopy className="w-5 h-5 text-emerald-400" />

            )}
            </button>
          </div>
          <div className=" p-4 rounded-lg text-gray-300">
            <MarkdownRenderer markdown={report.improved_summary_section} />         
            </div>
        </div>
      </div>
  </div>
)}

     
          

          

          

          {/* Feature highlights */}
          <div className="mt-20 grid grid-cols-1 md:grid-cols-3 gap-6 w-full max-w-6xl">
            {[
              {
                title: "Keyword Enhancement",
                description: "Identifies and incorporates key terms from the job description that match your skills",
                icon: <FiTarget className="w-8 h-8" />
              },
              {
                title: "ATS Compatibility",
                description: "Restructures your resume to ensure it passes through Applicant Tracking Systems",
                icon: <FiCheckCircle className="w-8 h-8" />
              },
              {
                title: "Skill Highlighting",
                description: "Emphasizes your most relevant qualifications for the specific position",
                icon: <FiAward className="w-8 h-8" />
              }
            ].map((feature, index) => (
              <div key={index} className="bg-gray-900/30 rounded-xl p-6 border border-gray-800 hover:border-emerald-500/40 transition-colors group">
                <div className="text-emerald-400 mb-4 group-hover:text-emerald-300 transition-colors">{feature.icon}</div>
                <h3 className="text-lg font-semibold text-white mb-2">{feature.title}</h3>
                <p className="text-gray-400">{feature.description}</p>
              </div>
            ))}
          </div>

          {/* Statistics */}
          <div className="mt-16 max-w-4xl w-full">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-center">
              {[
                { value: "93%", label: "Success Rate" },
                { value: "2.5×", label: "More Interviews" },
                { value: "85%", label: "Time Saved" },
                { value: "100K+", label: "Resumes Optimized" }
              ].map((stat, index) => (
                <div key={index} className="p-4">
                  <div className="text-2xl md:text-3xl font-bold text-emerald-400 mb-1">{stat.value}</div>
                  <div className="text-sm text-gray-400">{stat.label}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
      
      {/* Background decorative elements */}
      <div className="fixed top-0 left-0 w-full h-full pointer-events-none overflow-hidden -z-10">
        <div className="absolute top-20 left-20 w-96 h-96 bg-emerald-600 rounded-full mix-blend-multiply filter blur-[120px] opacity-10" />
        <div className="absolute top-40 right-20 w-96 h-96 bg-cyan-600 rounded-full mix-blend-multiply filter blur-[120px] opacity-10" />
        <div className="absolute bottom-20 left-1/2 w-96 h-96 bg-teal-600 rounded-full mix-blend-multiply filter blur-[120px] opacity-10" />
      </div>
    </div>
  );
};

export default AIResumeOptimizer;
