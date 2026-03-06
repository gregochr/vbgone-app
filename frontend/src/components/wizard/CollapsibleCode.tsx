import { useState, useRef, useEffect } from 'react'
import { CodeBlock } from './CodeBlock'

interface Props {
  title: string
  code: string
  defaultOpen?: boolean
}

export function CollapsibleCode({ title, code, defaultOpen = false }: Props) {
  const [open, setOpen] = useState(defaultOpen)
  const contentRef = useRef<HTMLDivElement>(null)
  const [height, setHeight] = useState<number | undefined>(undefined)

  useEffect(() => {
    if (contentRef.current) {
      setHeight(contentRef.current.scrollHeight)
    }
  }, [code, open])

  return (
    <div className="collapsible-code">
      <button
        className="collapsible-header"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        <span className={`collapsible-chevron ${open ? 'open' : ''}`}>{'\u25B6'}</span>
        <span>{title}</span>
      </button>
      <div
        className="collapsible-body"
        style={{
          maxHeight: open ? height : 0,
          opacity: open ? 1 : 0,
        }}
        ref={contentRef}
      >
        <div className="collapsible-content">
          <CodeBlock code={code} />
        </div>
      </div>
    </div>
  )
}
