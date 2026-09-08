/* eslint-disable @typescript-eslint/no-explicit-any */
// app/api/ats-stream/route.ts
import { NextRequest } from 'next/server';
import OpenAI from "openai";

export const runtime = 'nodejs'; // Use Edge Runtime to prevent timeouts

const client = new OpenAI({
  apiKey: process.env.OPENAI_SECRET_KEY,
});

async function extractText(file: File): Promise<string> {
  const uint8Array = new Uint8Array(await file.arrayBuffer());

  if (file.type === "application/pdf") {
    const { extractText } = await import("unpdf");

    const result = await extractText(uint8Array);

    return result.text.join("\n\n");
  }

  if (
    file.type ===
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  ) {
    const mammoth = await import("mammoth");

    // mammoth still expects buffer → convert only here
    const buffer = Buffer.from(uint8Array);

    const result = await mammoth.extractRawText({ buffer });
    return result.value;
  }

  throw new Error("Unsupported file type");
}

export async function POST(req: NextRequest) {
  try {
    const formData = await req.formData();
    const file = formData.get("file") as File;
    const jobDescription = formData.get("jobDescription") as string;

    if (!file) throw new Error("File is required");
    if (!jobDescription) throw new Error("Job description is required");

    const resumeText = await extractText(file)

    // console.log(resumeText);
    // console.log(jobDescription);
    
    // Set up streaming response
    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      async start(controller) {
        try {
          // Initialize the OpenAI streaming response
          const response = await client.chat.completions.create({
            model: "gpt-4o-mini",
            messages: [
              {
                role: "system",
                content: `
You are a resume optimization expert. You guide users to improve their resumes and always responds with valid JSON adhering to this schema:

ATS (string): determine the percentage ATS match of resume with Job description

ATS_tips (string[]): Determine the tips that can help improve the ATS score against the job description

missing_skills(string[]): Determine all the missing keywords that should be present in the resume and give a detailed list of missing skills.

Summary (string): Analyze the summary section and mention how it can be improved and where is it lacking. mention not found in case the summary section is unavailable

section_heading(string): Analyze section heading and check if any more headings are required

job_title_match (string): Analyze whether current job title in resume is a match with job description

education (string): Check whether current education is enough for the job requirements

date_format (string): check if the date format is correct or not

job_level_match (string): Analyze the total years of experience and check whether resume is eligible or not

resume_tone (string): Analyze the resume tone and suggest further improvements

web_presence (string): Check whether resume has web presence or not, such as portfolios

improved_summary_section (raw markdown text): Analyze and Improve summary section in the resume, if found, following all the best resume practices and get a 100% ATS match so that users can copy the text and paste in in their resumes

improved_experience_section (raw markdown text): Analyze and Improve experience section in the resume, if found, following all the best resume practices and get a 100% ATS match so that users can copy the text and paste in in their resumes

improved_skills_section (raw markdown text): Analyze and Improve skills section in the resume, if found, following all the best resume practices and get a 100% ATS match so that users can copy the text and paste in in their resumes, all necessary skills for the job requirements
` // Same prompt as before
              },
              {
                role: "user",
                content: `Generate an ATS report in JSON format for this resume:\n\n${resumeText}\n\nBased on this job description:\n\n${jobDescription}`,
              },
            ],
            response_format: { "type": "json_object" },
            stream: true,
          });

          let jsonString = '';
          
          // Process each chunk as it arrives
          for await (const chunk of response) {
            const content = chunk.choices[0]?.delta?.content || '';
            jsonString += content;
            
            // Send chunk to client
            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ content, length: jsonString.length })}\n\n`));
          }
          
          // Signal completion
          controller.enqueue(encoder.encode(`data: ${JSON.stringify({ complete: true })}\n\n`));
        } catch (error: any) {
          controller.enqueue(encoder.encode(`data: ${JSON.stringify({ error: error.message || "Unknown error" })}\n\n`));
        } finally {
          controller.close();
        }
      }
    });
    
    return new Response(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      },
    });
  } catch (error: any) {
    return new Response(
      JSON.stringify({ error: error.message || "An error occurred" }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    );
  }
}
