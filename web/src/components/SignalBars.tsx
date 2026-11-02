import { signalBars } from '../lib/modules'

export function SignalBars({ score }: { score: number }) {
  const bars = signalBars(score)
  return (
    <span className="inline-flex items-end gap-[2px]" aria-label={`Signal ${score} sur 10`}>
      {[1, 2, 3, 4].map((bar) => (
        <span
          key={bar}
          className={`w-[4px] rounded-sm ${bar <= bars ? (bars <= 1 ? 'bg-ios-orange' : 'bg-current') : 'bg-neutral-300 dark:bg-neutral-600'}`}
          style={{ height: 3 + bar * 3 }}
        />
      ))}
    </span>
  )
}
