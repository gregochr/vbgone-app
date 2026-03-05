import { useState, useRef, useEffect } from 'react'

interface Props {
  children: React.ReactNode
}

export function InfoTip({ children }: Props) {
  const [open, setOpen] = useState(false)
  const tipRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handleClick = (e: MouseEvent) => {
      if (tipRef.current && !tipRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  return (
    <span className="infotip-wrapper" ref={tipRef}>
      <button
        className="infotip-trigger"
        onClick={(e) => {
          e.stopPropagation()
          setOpen((o) => !o)
        }}
        aria-label="More info"
        type="button"
      >
        About this demo
      </button>
      {open && (
        <div className="infotip-popover">
          <button
            className="infotip-close"
            onClick={() => setOpen(false)}
            aria-label="Close"
            type="button"
          >
            {'\u2715'}
          </button>
          {children}
        </div>
      )}
    </span>
  )
}
