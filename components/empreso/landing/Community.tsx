

const testimonials = [
    {
      quote: "I was struggling to get interview calls, but Empreso helped me refine my resume and improve my coding skills. Now, I'm working as a Python Developer at a top company!",
      name: "Shashank",
      title: "Python Developer"
    },
    {
      quote: "Empreso gave me the confidence and skills I needed to secure my first job as a Software Engineer. Their expert career coaching and technical training made all the difference!",
      name: "Saketh",
      title: "Software Engineer"
    },
    {
      quote: "Empreso's mentorship and career guidance turned my job search around. I went from feeling stuck to landing a Java Backend Developer role in just a few months!",
      name: "Siddu Ramagiri",
      title: "Backend Developer"
    },
    {
      quote: "Breaking into tech seemed impossible until I found Empreso! Their guidance helped me land my first role as a Full-Stack Developer. I can't thank them enough for making my dream a reality!",
      name: "Vanshaj Gugnani",
      title: "Full-Stack Developer"
    },
    {
      quote: "With Empreso's help, I transitioned from a non-tech background to a Frontend Developer role. Their personalized coaching and projects gave me real-world experience.",
      name: "Ethan Evans",
      title: "Frontend Developer"
    },
    {
      quote: "I kept getting rejections until I worked with Empreso. They prepared me for coding interviews and taught me industry best practices. Now, I'm a proud Software Developer!",
      name: "Fiona Foster",
      title: "Software Developer"
    },
    {
      quote: "Empreso's hands-on approach to learning helped me land a role as an Angular Developer. I highly recommend them to anyone trying to break into IT!",
      name: "George Garcia",
      title: "Angular Developer"
    },
    
    {
      quote: "I thought it would take years to get into tech, but thanks to Empreso, I secured an Application Developer job within months!",
      name: "Hannah Hill",
      title: "Application Developer"
    },
    {
      quote: "Empreso is a game-changer! Their job placement support and technical training helped me land my first IT role. I couldn't be happier!",
      name: "Isaac Ingram",
      title: "IT Professional"
    },
    {
      quote: "I was overwhelmed by the job search process, but Empreso guided me every step of the way. Now, I'm thriving as a Full-Stack Developer!",
      name: "Jessica Johnson",
      title: "Full-Stack Developer"
    },
    {
      quote: "Empreso's career coaching and mock interviews helped me land my first software engineering job. Their approach is truly effective!",
      name: "Kevin King",
      title: "Software Engineer"
    },
    {
      quote: "Thanks to Empreso, I went from zero experience to a Mainstack Developer role. Their bootcamps and career guidance made all the difference!",
      name: "Laura Lewis",
      title: "Mainstack Developer"
    },
    {
      quote: "Empreso made job hunting easier by helping me build a strong portfolio and ace technical interviews. Now, I'm working as a Backend Engineer!",
      name: "Michael Moore",
      title: "Backend Engineer"
    },
    {
      quote: "I was skeptical at first, but Empreso's approach worked! Their tailored job search strategies helped me secure a Python Developer position.",
      name: "Nina Nelson",
      title: "Python Developer"
    },
    {
      quote: "Empreso turned my passion for tech into a career! Their training and job placement support helped me become a Full-Stack Developer in no time.",
      name: "Oliver Owens",
      title: "Full-Stack Developer"
    }
  ]

const loopTestimonials = [...testimonials, ...testimonials];

export const Community = () => {
  return (
    <section className="relative border-t border-white/[0.1] overflow-hidden">
      <div className="mx-auto grid max-w-7xl grid-cols-1 gap-10 px-6 py-10 lg:grid-cols-2">
        
        {/* LEFT: Marquee Testimonials */}
        <div className="relative overflow-hidden">
          <div className="flex animate-marquee-left gap-6 flex-cols-1">
            {loopTestimonials.map((item, i) => (
              <TestimonialCard key={i} item={item} />
            ))}
          </div>
        </div>

        {/* RIGHT: Static heading */}
        <div className="flex items-center justify-end">
          <h2 className="font-mono-display text-5xl font-bold tracking-tight sm:text-6xl md:text-7xl">
            <span className="text-muted-foreground">//</span> community
          </h2>
        </div>

      </div>
    </section>
  );
};

type itemType = {
  quote : string;
  name : string;
  title: string;
}

function TestimonialCard({ item } : {item : itemType}) {
  return (
    <div className="min-w-[300px] max-w-[320px] p-5 rounded-none backdrop-blur-sm">
      
      <p className="text-sm text-muted-foreground leading-relaxed">
        "{item.quote}"
      </p>

      <div className="mt-4 flex items-center gap-3">
        <div>
          <p className="text-sm font-semibold">{item.name}</p>
          <p className="text-xs text-muted-foreground">{item.title}</p>
        </div>
      </div>

    </div>
  );
}