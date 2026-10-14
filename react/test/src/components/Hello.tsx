type HelloProps = {
  name: string
}

export default function Hello({ name }: HelloProps) {
  return <p className="hello">Hello, {name || 'world'}!</p>
}
