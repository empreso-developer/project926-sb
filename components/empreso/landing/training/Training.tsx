'use client'

import { 
  Code, 
  Database, 
  Cloud, 
  ServerCog, 
  Workflow, 
  Shield, 
  Brain, 
  Network 
} from 'lucide-react'
import { useRef } from "react";
import { useInView } from "react-intersection-observer";

const categories = [
    {
      id: "full-stack-development",
      icon: Code,
      title: "Full Stack / Backend / Frontend Development",
      topics: [
        "Java, Spring Boot",
        "Node.js, Express.js",
        "React, Angular, Vue.js",
        "HTML, CSS, JavaScript",
        "RESTful APIs, Microservices",
        "SQL & NoSQL Databases",
        "Authentication, Security",
        "DevOps Integration"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance for financial & security-sensitive companies",
          "Resume & LinkedIn Optimization",
          "Job Marketing on Indeed & Monster",
          "Local Vendor Marketing for Direct Placements"
        ]
      },
      jobMarket: {
        growth: "32% increase in 2024, expected to rise 18% more in 2025",
        cities: ["Toronto", "Vancouver", "Montreal", "Calgary", "Ottawa"],
        jobTypes: ["Remote", "Hybrid", "Contract & Full-Time"],
        salaries: {
          entry: "CAD $70K - $90K",
          experienced: "CAD $100K - $140K"
        }
      }
    },
    {
      id: "data-engineering",
      icon: Database,
      title: "Data Engineering & Analytics",
      topics: [
        "SQL",
        "ETL Pipelines",
        "Apache Spark, Hadoop",
        "Power BI, Tableau",
        "Real-time Data Processing",
        "Kafka, Airflow",
        "Cloud Pipelines"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance for financial & government roles",
          "Resume & Portfolio Building",
          "Employer Referrals"
        ]
      },
      jobMarket: {
        growth: "63% increase in 2024, expected to reach 80% by 2025",
        cities: ["Toronto", "Montreal", "Calgary", "Edmonton", "Winnipeg"],
        jobTypes: ["Remote", "Hybrid", "Full-Time"],
        salaries: {
          entry: "CAD $75K - $95K",
          experienced: "CAD $100K - $150K"
        }
      }
    },
    {
      id: "cloud-engineer",
      icon: Cloud,
      title: "AWS / Azure / Google Cloud Engineer",
      topics: [
        "AWS, Azure, GCP Infrastructure",
        "Cloud Security",
        "Kubernetes, Docker",
        "Terraform, CI/CD Pipelines",
        "Serverless Computing",
        "High-Availability Architecture"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance for banking & cloud security roles",
          "Certifications & Resume Marketing",
          "Employer Networking"
        ]
      },
      jobMarket: {
        growth: "80% cloud adoption in 2024, increasing to 92% by 2025",
        cities: ["Toronto", "Vancouver", "Calgary", "Halifax", "Ottawa"],
        jobTypes: ["Hybrid", "Contract & Freelance"],
        salaries: {
          entry: "CAD $80K - $100K",
          experienced: "CAD $110K - $160K"
        }
      }
    },
    {
      id: "cloud-data-engineering",
      icon: ServerCog,
      title: "Cloud Data Engineering (AWS, Azure, GCP)",
      topics: [
        "AWS Redshift",
        "Google BigQuery",
        "Azure Synapse",
        "ETL Pipelines",
        "Apache Spark",
        "Dataflow, Data Factory",
        "Cloud Data Warehousing",
        "Analytics"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance for sensitive data handling roles",
          "Certifications",
          "LinkedIn Optimization",
          "Vendor Outreach"
        ]
      },
      jobMarket: {
        growth: "67% in 2024, rising to 90% in 2025",
        cities: ["Toronto", "Vancouver", "Montreal", "Calgary", "Mississauga"],
        jobTypes: ["Remote", "Hybrid", "Contract-Based"],
        salaries: {
          entry: "CAD $85K - $105K",
          experienced: "CAD $120K - $160K"
        }
      }
    },
    {
      id: "devops-automation",
      icon: Workflow,
      title: "DevOps & Automation",
      topics: [
        "CI/CD Pipelines",
        "Jenkins, GitHub Actions",
        "Infrastructure as Code",
        "Terraform, Ansible",
        "Kubernetes",
        "Cloud DevOps for AWS, Azure, GCP"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance for security-sensitive DevOps roles",
          "Certifications",
          "Employer Targeting",
          "Resume Optimization"
        ]
      },
      jobMarket: {
        growth: "94% of companies invested in DevOps in 2024, rising to 98% by 2025",
        cities: ["Toronto", "Vancouver", "Montreal", "Kitchener-Waterloo"],
        jobTypes: ["Remote", "Hybrid", "Short-Term Contracts"],
        salaries: {
          entry: "CAD $90K - $110K",
          experienced: "CAD $120K - $170K"
        }
      }
    },
    {
      id: "cyber-security",
      icon: Shield,
      title: "Cyber Security",
      topics: [
        "SIEM Tools",
        "Cloud Security",
        "Network Security",
        "Ethical Hacking",
        "Risk Management",
        "Compliance"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance required for 80% of cybersecurity jobs",
          "Security Certifications (CompTIA Security+, CEH)",
          "Employer Referrals"
        ]
      },
      jobMarket: {
        growth: "60% increase in 2024, expected to jump 75% in 2025",
        cities: ["Toronto", "Ottawa", "Calgary", "Halifax", "Winnipeg"],
        jobTypes: ["Full-Time", "Contract", "Remote"],
        salaries: {
          entry: "CAD $85K - $110K",
          experienced: "CAD $130K - $180K"
        }
      }
    },
    {
      id: "data-science-ai-ml",
      icon: Brain,
      title: "Data Scientist, AI & ML",
      topics: [
        "Machine Learning",
        "TensorFlow, PyTorch",
        "AI Prompt Engineering",
        "NLP Model Development"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance for AI & financial roles",
          "Certifications",
          "Resume Optimization",
          "Employer Networking"
        ]
      },
      jobMarket: {
        growth: "110% increase in 2024, adding another 40% in 2025",
        cities: ["Toronto", "Montreal", "Vancouver", "Waterloo", "Edmonton"],
        jobTypes: ["Remote", "Hybrid", "AI Startups Hiring"],
        salaries: {
          entry: "CAD $90K - $120K",
          experienced: "CAD $130K - $180K"
        }
      }
    },
    {
      id: "multi-cloud-hybrid",
      icon: Network,
      title: "Multi-Cloud & Hybrid Solutions",
      topics: [
        "Multi-Cloud Deployment",
        "AWS, Azure, GCP",
        "Hybrid Cloud Security",
        "Hybrid Cloud Automation"
      ],
      careerReadiness: {
        points: [
          "Background Check Clearance for security & compliance roles",
          "Certifications",
          "Resume Optimization",
          "Job Market Outreach"
        ]
      },
      jobMarket: {
        growth: "73% increase in hybrid cloud adoption (2024), reaching 90% by 2025",
        cities: ["Toronto", "Vancouver", "Calgary", "Ottawa", "Mississauga"],
        jobTypes: ["Hybrid", "Remote", "Cloud Architect Roles"],
        salaries: {
          entry: "CAD $85K - $105K",
          experienced: "CAD $120K - $160K"
        }
      }
    }
  ];

export default function TrainingPage() {
  const containerRef = useRef<HTMLDivElement>(null)
  return (
    <main className="relative min-h-screen bg-background text-foreground">
      {/* GRID BACKGROUND */}
      <div className="pointer-events-none absolute inset-0 grid-bg opacity-40" />

      {/* HERO */}
      <section className="relative mx-auto max-w-5xl px-6 py-24 text-center">
        <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
          Empreso Training
        </p>

        <h1 className="mt-6 font-mono-display text-5xl leading-tight sm:text-6xl">
          Industry-Ready Skills & Career Paths
        </h1>

        <p className="mx-auto mt-6 max-w-2xl text-muted-foreground">
          Structured training programs designed for real-world hiring demand across
          cloud, AI, data, and full-stack engineering.
        </p>
      </section>

      {/* SECTIONS */}
      <section className="relative pb-32 space-y-28" ref={containerRef}>
        {categories.map((cat) => {
          const { ref, inView } = useInView({
              threshold: 0.2,
              triggerOnce: true,
            });
          
          return (
          <div key={cat.id} id={cat.id} className="scroll-mt-24" ref={ref}>

            <div className="mx-auto max-w-7xl px-6 mb-10">
              <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">
                {cat.title}
              </h2>
            </div>

            <div className="border-y bg-neutral-950 border-white/[0.1]">
              <div className="mx-auto max-w-7xl px-6">
                <CategoryCard cat={cat} />
              </div>
            </div>

          </div>
        )})}
      </section>
    </main>
  );
}

type Category = {
  id: string;
  icon: any;
  title: string;
  topics: string[];
  careerReadiness: {
    points: string[];
  };
  jobMarket: {
    growth: string;
    cities: string[];
    jobTypes: string[];
    salaries: {
      entry: string;
      experienced: string;
    };
  };
};

type Props = {
  cat: Category;
};

function CategoryCard({ cat }: Props) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 border border-white/[0.1] divide-y lg:divide-y-0 lg:divide-x divide-border/60 bg-opacity-25">
      {/* TOPICS */}
      <div className="p-6">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Topics Covered
        </h3>

        <ul className="mt-4 space-y-2 text-sm text-muted-foreground">
          {cat.topics.map((t) => (
            <li key={t} className="flex gap-2">
              <span className="text-primary">•</span> {t}
            </li>
          ))}
        </ul>
      </div>

      {/* CAREER READINESS */}
      <div className="p-6">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Career Readiness
        </h3>

        <ul className="mt-4 space-y-2 text-sm text-muted-foreground">
          {cat.careerReadiness.points.map((p) => (
            <li key={p} className="flex gap-2">
              <span className="text-green-500">✓</span> {p}
            </li>
          ))}
        </ul>
      </div>

      {/* JOB MARKET */}
      <div className="p-6">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Job Market Outlook
        </h3>

        <div className="mt-4 space-y-4 text-sm">

          <p className="text-muted-foreground">
            <span className="font-medium text-foreground">Project Growth:</span>{" "}
            {cat.jobMarket.growth}
          </p>

          <div>
            <p className="font-medium text-foreground">High-Demand Cities</p>
            <p className="text-muted-foreground">
              {cat.jobMarket.cities.join(", ")}
            </p>
          </div>

          <div>
            <p className="font-medium text-foreground">Jobs Types</p>
            <p className="text-muted-foreground">
              {cat.jobMarket.jobTypes.join(", ")}
            </p>
          </div>

          <div>
            <p className="font-medium text-foreground">Salary Ranges</p>
            <p className="text-muted-foreground">
              Entry: {cat.jobMarket.salaries.entry}
              <br />
              Senior: {cat.jobMarket.salaries.experienced}
            </p>
          </div>

        </div>
      </div>

    </div>
  );
}