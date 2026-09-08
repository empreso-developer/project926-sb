'use client'
import 'react-toastify/dist/ReactToastify.css'
import { FileUpload } from './ui/file-upload'


const NewResume = ({ onFileSelect } : { onFileSelect : (file: File) => void }) => {

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];

    if (!file) return;

    onFileSelect(file);
  };


  return (
    <>
    <div className="w-full max-w-4xl mx-auto min-h-96   bg-black border-neutral-800 rounded-lg">
      <FileUpload onChange={handleChange} />
    </div>
    </>
  )
}

export default NewResume
