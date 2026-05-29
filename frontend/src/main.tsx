import { render } from 'preact'
import { LocationProvider } from 'preact-iso'
import { App } from './app'
import { bootstrap } from './lib/auth'
import './index.css'

void bootstrap()

const root = document.getElementById('app')
if (root) {
  render(
    <LocationProvider>
      <App />
    </LocationProvider>,
    root,
  )
}
