interface ToggleProps {
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
  label: string
}

/** iOS switch. */
export function Toggle({ checked, onChange, disabled, label }: ToggleProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={(event) => {
        event.stopPropagation()
        onChange(!checked)
      }}
      className={`relative h-[31px] w-[51px] shrink-0 rounded-full transition-colors duration-200 disabled:opacity-50 ${
        checked ? 'bg-ios-green' : 'bg-neutral-300 dark:bg-neutral-600'
      }`}
    >
      <span
        className={`absolute top-[2px] left-[2px] size-[27px] rounded-full bg-white shadow-md transition-transform duration-200 ${
          checked ? 'translate-x-5' : ''
        }`}
      />
    </button>
  )
}
