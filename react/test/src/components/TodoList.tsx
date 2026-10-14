import { useState } from 'react'

type Todo = {
  id: number
  text: string
}

export default function TodoList() {
  const [todos, setTodos] = useState<Todo[]>([
    { id: 1, text: 'Open this project in Ghost IDE' },
    { id: 2, text: 'Run the install setup action once' },
  ])
  const [text, setText] = useState('')

  function addTodo() {
    const value = text.trim()
    if (!value) {
      return
    }
    setTodos((current) => [...current, { id: Date.now(), text: value }])
    setText('')
  }

  return (
    <section className="todos">
      <h2>Todos</h2>
      <ul>
        {todos.map((todo) => (
          <li key={todo.id}>{todo.text}</li>
        ))}
      </ul>
      <div className="todo-add">
        <input
          value={text}
          placeholder="New todo"
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              addTodo()
            }
          }}
        />
        <button type="button" onClick={addTodo}>
          Add
        </button>
      </div>
    </section>
  )
}
