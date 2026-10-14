# React test project

A small React + TypeScript (Vite) app used to exercise the **React Support**
plugin inside Ghost IDE.

## What to check

- Open `src/App.tsx` or any `.tsx` file: the TypeScript language server should
  attach (completion, hover, diagnostics, go to definition, rename, inlay hints).
- TextMate highlighting for JSX/TSX works even before the server is ready.
- Press **Run React** from the editor toolbar (or run the `Run React` command):
  the plugin runs the `dev` script and the Vite dev server starts in the
  terminal.

## First run

Run the plugin's install setup action once from the Plugin Manager so that
Node, `typescript` and `typescript-language-server` are available in the proot
environment. The plugin installs dependencies automatically on the first run.

## Manual run (optional)

```
npm install
npm run dev
```

The dev server listens on port `5173`.
