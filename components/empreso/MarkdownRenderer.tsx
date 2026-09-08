import React, { useMemo } from 'react';
import MarkdownIt from 'markdown-it';

interface MarkdownRendererProps {
  markdown: string;
}

const stripCodeFence = (input: string): string => {
  // Matches ```lang\n ... ``` at start/end, capturing the inner content
  const fenceRegex = /^```[^\n]*\n([\s\S]*?)```$/;
  const match = input.match(fenceRegex);
  return match ? match[1] : input;
};

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ markdown }) => {
  // Initialize markdown-it once
  const md = useMemo(() => new MarkdownIt(), []);

  // Strip optional code-fence and render HTML
  const htmlContent = useMemo(() => {
    const content = stripCodeFence(markdown);
    // console.log(content)
    return md.render(content);
  }, [markdown, md]);

  return (
    <div
      className="prose prose-invert text-left"
      dangerouslySetInnerHTML={{ __html: htmlContent }}
    />
  );
};

export default MarkdownRenderer;
