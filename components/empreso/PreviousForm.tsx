'use client'
import React, { useState } from 'react'
import { useRouter } from 'next/navigation'
import { toast, ToastContainer } from 'react-toastify'

export async function previousResume(formData: FormData) {
  const userId = formData.get("userId")
  const jobId = formData.get("jobId")

  // Process the data, e.g., save it in a database
  console.log("User ID:", userId)
  console.log("Job ID:", jobId)

  // Dummy response for example purposes.
  // Replace with your actual logic which might throw an error if unsuccessful.
  return { success: true }
}

const PreviousForm = ({ userId, jobId }: { userId: string; jobId: string }) => {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const router = useRouter()

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setIsSubmitting(true)
    const formData = new FormData(e.currentTarget)

    try {
      const response = await previousResume(formData)
      if (response.success) {
        toast.success("Submission Successful!")
        // Redirect after a short delay to allow the toast to be seen
        setTimeout(() => {
          router.push("/jobs")
        }, 2000)
      } else {
        toast.error("Submission Unsuccessful!")
      }
    } catch (error) {
      toast.error("An error occurred!")
      console.error("Submission error:", error)
    }
    setIsSubmitting(false)
  }

  return (
    <>
      <form onSubmit={handleSubmit}>
        <input type="hidden" name="userId" value={userId} />
        <input type="hidden" name="jobId" value={jobId} />
        <button
          type="submit"
          disabled={isSubmitting}
          className={`w-full transform rounded-lg bg-gradient-to-r from-emerald-600 to-cyan-600 px-3 py-2 text-xs font-medium text-white transition hover:scale-[1.02] hover:shadow-lg sm:px-4 sm:py-2.5 sm:text-sm ${
            isSubmitting ? "opacity-50 cursor-not-allowed" : ""
          }`}
        >
          {isSubmitting ? "Submitting..." : "Submit"}
        </button>
      </form>
      <ToastContainer 
        theme="dark" // This applies a dark theme (black background)
        position="top-right"
        autoClose={5000}
        hideProgressBar={false}
        newestOnTop={false}
        closeOnClick
        rtl={false}
        pauseOnFocusLoss
        draggable
        pauseOnHover
      />
    </>
  )
}

export default PreviousForm
