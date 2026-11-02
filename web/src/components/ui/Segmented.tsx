interface SegmentedProps<T extends string> {
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
  label: string
}

/** iOS segmented control. */
export function Segmented<T extends string>({ value, options, onChange, label }: SegmentedProps<T>) {
  return (
    <div role="radiogroup" aria-label={label} className="flex w-full rounded-[9px] bg-neutral-200/80 p-0.5 dark:bg-neutral-700/60">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className={`flex-1 rounded-[7px] px-2 py-1 text-[13px] font-medium transition ${
            value === option.value ? 'bg-white shadow-sm dark:bg-neutral-500' : 'text-neutral-600 dark:text-neutral-300'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
