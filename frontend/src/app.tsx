import { Route, Router } from 'preact-iso'
import { Header } from './components/Header'
import { RequireAuth } from './components/RequireAuth'
import { Files } from './pages/Files'
import { Login } from './pages/Login'
import { NotFound } from './pages/NotFound'
import { Register } from './pages/Register'

const FilesPage = () => (
  <RequireAuth>
    <Files />
  </RequireAuth>
)

export function App() {
  return (
    <div class="min-h-screen bg-slate-50 text-slate-900">
      <Header />
      <main class="mx-auto w-full max-w-3xl px-4 py-8">
        <Router>
          <Route path="/login" component={Login} />
          <Route path="/register" component={Register} />
          <Route path="/" component={FilesPage} />
          <Route default component={NotFound} />
        </Router>
      </main>
    </div>
  )
}
