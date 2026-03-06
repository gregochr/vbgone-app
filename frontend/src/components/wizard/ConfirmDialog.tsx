interface Props {
  message?: string
  children?: React.ReactNode
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ message, children, onConfirm, onCancel }: Props) {
  return (
    <div className="confirm-overlay" onClick={onCancel}>
      <div className="confirm-dialog" onClick={(e) => e.stopPropagation()}>
        {children ? (
          <div className="confirm-message">{children}</div>
        ) : (
          <p className="confirm-message">{message}</p>
        )}
        <div className="confirm-actions">
          <button className="btn-plex" onClick={onCancel}>
            Cancel
          </button>
          <button className="btn-plex confirm-continue" onClick={onConfirm}>
            Continue
          </button>
        </div>
      </div>
    </div>
  )
}
