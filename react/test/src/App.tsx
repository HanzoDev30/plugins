import { useState } from 'react'
import Hello from './components/Hello'
import Counter from './components/Counter'
import TodoList from './components/TodoList'
import './App.css'

export default function App() {
  const [name, setName] = useState('React')

  return (
    <main className="app">
      <h1>React + TypeScript</h1>
      <p className="subtitle">
        A small test project for the Ghost IDE React plugin.
      </p>

      <label className="field">
        <span>Name</span>
        <input value={name} onChange={(event) => setName(event.target.value)} />
      </label>

      <Hello name={name} />
      <Hello name="ای کیرم تو GG"/>
      <Counter initial={0} />
      <TodoList />
    </main>
  )
}
