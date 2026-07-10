export default function TodoItem({ todo, onToggle, onDelete }) {
  return (
    <li className={`todo-item${todo.completed ? " is-complete" : ""}`}>
      <button
        type="button"
        className="check"
        role="checkbox"
        aria-checked={todo.completed}
        aria-label={todo.completed ? "Mark as not done" : "Mark as done"}
        onClick={() => onToggle(todo)}
      >
        {todo.completed ? "✓" : ""}
      </button>

      <span className="todo-text">{todo.text}</span>

      <button
        type="button"
        className="delete"
        aria-label={`Delete "${todo.text}"`}
        onClick={() => onDelete(todo)}
      >
        Delete
      </button>
    </li>
  );
}
