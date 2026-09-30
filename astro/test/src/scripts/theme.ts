const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

document.documentElement.dataset.theme = prefersDark ? 'dark' : 'light';

export function toggleTheme(force?: boolean) {
  const next = force ?? document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  return next;
}