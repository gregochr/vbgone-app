import { useState } from 'react'

interface Props {
  message: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ message, onConfirm, onCancel }: Props) {
  const [dontShow, setDontShow] = useState(false)

  return (
    <div className="confirm-overlay" onClick={onCancel}>
      <div className="confirm-dialog" onClick={(e) => e.stopPropagation()}>
        <p className="confirm-message">{message}</p>
        <label className="confirm-checkbox-label">
          <input
            type="checkbox"
            checked={dontShow}
            onChange={(e) => setDontShow(e.target.checked)}
          />
          Don&apos;t show again this session
        </label>
        <div className="confirm-actions">
          <button className="btn-back" onClick={onCancel}>
            Cancel
          </button>
          <button
            className="btn-next"
            onClick={() => {
              if (dontShow) {
                sessionStorage.setItem('vbgone-skip-confirm', 'true')
              }
              onConfirm()
            }}
          >
            Continue
          </button>
        </div>
      </div>
    </div>
  )
}

export function shouldSkipConfirm(): boolean {
  return sessionStorage.getItem('vbgone-skip-confirm') === 'true'
}
