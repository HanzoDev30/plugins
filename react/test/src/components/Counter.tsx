import { useState } from 'react'

type CounterProps = {
  initial?: number
}

export default function Counter({ initial = 0 }: CounterProps) {
  const [count, setCount] = useState(initial)

  return (
    <div className="counter">
      <button type="button" onClick={() => setCount((value) => value - 1)}>
        -
      </button>
      <span className="counter-value">{count}</span>
      <button type="button" onClick={() => setCount((value) => value + 1)}>
        +
      </button>
    </div>
  )
}
