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


export function MobileSkillCard() {
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
      <div id="skills-mobile" className="block md:hidden">
        <div className="grid grid-cols-2 gap-4 px-4 relative z-10 py-6">
          {features.map((feature) => (
            <MobileFeature key={feature.title} {...feature} />
          ))}
        </div>
      </div>
    );
  }
  
  const MobileFeature = ({
    id,
    title,
    description,
    icon,
  }: {
    id: string;
    title: string;
    description: string;
    icon: React.ReactNode;
  }) => {
    const descriptionList = description.split("\n");
  
    return (
      <Link
        href={`/training/#${id}`}
        className="group/mobileFeature relative flex flex-col p-4 rounded-xl bg-neutral-800/50 hover:bg-neutral-800 transition-colors border border-neutral-700"
      >
        <div className="mb-2 text-blue-400">{icon}</div>
        <h3 className="text-sm font-semibold text-neutral-100 mb-1.5 line-clamp-2">
          {title}
        </h3>
        <ul className="text-xs text-neutral-400 space-y-0.5">
          {descriptionList.map((point, index) => (
            <li key={index} className="line-clamp-1">
              {point}
            </li>
          ))}
        </ul>
      </Link>
    );
  };
  