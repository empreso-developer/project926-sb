import { cn } from "@/lib/utils";
import {
  IconTerminal2,
  IconCloud,
  IconServer,
  IconShieldLock,
  IconChartBar,
  IconRobot,
  IconCloudOff,
} from "@tabler/icons-react";
import Link from "next/link";

export function SkillCard() {
  const features = [
    {
      id: "full-stack-development",
      title: "Full Stack / Backend / Frontend Development",
      description:
        " Training in Java, Spring Boot, Node.js, Angular, React & Databases\n Coaching & job placement for Software Engineers & Developers",
      icon: <IconTerminal2 />,
    },
    {
      id: "data-engineering",
      title: "Data Engineering & Analytics",
      description:
        " Learn ETL, SQL, Apache Spark, Power BI, Tableau\n Job placement in Data Engineering & Analytics",
      icon: <IconChartBar />,
    },
    {
      id: "cloud-engineer",
      title: "AWS Cloud Engineer / Azure Cloud Engineer / Google Cloud Engineer",
      description:
        " Master Cloud Architecture, Kubernetes, Terraform, DevOps\n Placement assistance for Cloud Engineers",
      icon: <IconCloud />,
    },
    {
      id: "cloud-data-engineering",
      title: "Cloud Data Engineering (AWS, Azure, GCP)",
      description:
        " Training in AWS Redshift, Google BigQuery, Azure Synapse\n Job roles in Cloud Data Engineering",
      icon: <IconCloudOff />,
    },
    {
      id: "devops-automation",
      title: "DevOps & Automation",
      description:
        " Hands-on CI/CD, Jenkins, Ansible, Docker, Kubernetes\n Placement in DevOps & Automation roles",
      icon: <IconServer />,
    },
    {
      id: "cyber-security",
      title: "Cyber Security",
      description:
        " Learn SIEM tools, ethical hacking, risk management\n Coaching for Cybersecurity jobs",
      icon: <IconShieldLock />,
    },
    {
      id: "data-science-ai-ml",
      title: "Data Scientist, AI & ML",
      description:
        " Training in AI, Machine Learning, Prompt Engineering\n Placement in Data Science & AI roles",
      icon: <IconRobot />,
    },
    {
      id: "multi-cloud-hybrid",
      title: "Multi-Cloud & Hybrid Solutions",
      description:
        " Master Hybrid Cloud, Automation, Security\n Job opportunities for Cloud Architects & Engineers",
      icon: <IconCloud />,
    },
  ];

  return (
    <div id="skills">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 relative z-10 py-10 max-w-7xl mx-auto">
        {features.map((feature, index) => (
          <Feature key={feature.title} {...feature} index={index} />
        ))}
      </div>
    </div>
  );
}

const Feature = ({
  id,
  title,
  description,
  icon,
  index,
}: {
  id: string,
  title: string;
  description: string;
  icon: React.ReactNode;
  index: number;
}) => {
  // Split the description by newline (\n) to create an array of points
  const descriptionList = description.split("\n");

  return (
    <div
      className={cn(
        "flex flex-col lg:border-r py-10 relative group/feature border-neutral-800",
        (index === 0 || index === 4) && "lg:border-l border-neutral-800",
        index < 4 && "lg:border-b border-neutral-800"
      )}
    >
      <Link href={`/training/#${id}`}>
      {index < 4 && (
        <div className="opacity-0 group-hover/feature:opacity-100 transition duration-200 absolute inset-0 h-full w-full bg-gradient-to-t from-neutral-800 to-transparent pointer-events-none" />
      )}
      {index >= 4 && (
        <div className="opacity-0 group-hover/feature:opacity-100 transition duration-200 absolute inset-0 h-full w-full bg-gradient-to-b from-neutral-800 to-transparent pointer-events-none" />
      )}
      <div className="mb-4 relative z-10 px-10 text-neutral-400">{icon}</div>
      <div className="text-lg font-bold mb-2 relative z-10 px-10">
        <div className="absolute left-0 inset-y-0 h-6 group-hover/feature:h-8 w-1 rounded-tr-full rounded-br-full bg-neutral-700 group-hover/feature:bg-emerald-400 transition-all duration-200 origin-center" />
        <span className="group-hover/feature:translate-x-2 transition duration-200 inline-block text-neutral-100">
          {title}
        </span>
      </div>
      <ul className="text-sm text-neutral-300 max-w-xs relative z-10 px-10">
        {descriptionList.map((point, index) => (
          <li key={index} className="list-disc pl-5 mb-2">
            {point}
          </li>
        ))}
      </ul>
      </Link>
    </div>
  );
};
