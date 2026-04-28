import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import type { FormEvent, ReactNode } from 'react'
import heroImg from './assets/hero.png'
import './App.css'

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

type EventResponse = {
  id: string
  status: string
  organizerName: string
  title: string
  description: string
  location: string
  eventDate: string
  imageUrl?: string | null
}

type PageResponse<T> = {
  content: T[]
  totalPages: number
  totalElements: number
  size: number
  number: number
}

type AuthTokens = {
  accessToken: string
  refreshToken: string
}

type AuthState = {
  accessToken: string | null
  refreshToken: string | null
  profileName: string
}

type JwtClaims = {
  sub?: string
  role?: string
  userId?: string
  exp?: number
}

type AuthContextValue = {
  auth: AuthState
  claims: JwtClaims | null
  isAuthenticated: boolean
  profileName: string
  setProfileName: (name: string) => void
  login: (email: string, password: string) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => Promise<void>
  authFetch: (path: string, options?: RequestInit) => Promise<Response>
}

type RegisterPayload = {
  email: string
  password: string
  fullName: string
}

type Route =
  | { name: 'home' }
  | { name: 'event'; id: string }
  | { name: 'login' }
  | { name: 'register' }
  | { name: 'organizer' }
  | { name: 'organizer-new' }
  | { name: 'organizer-edit'; id: string }
  | { name: 'tickets' }

const AuthContext = createContext<AuthContextValue | null>(null)

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function App() {
  const route = useHashRoute()
  const [auth, setAuth] = useState<AuthState>(() => loadAuth())
  const refreshRef = useRef<Promise<AuthTokens | null> | null>(null)

  const claims = useMemo(() => decodeJwt(auth.accessToken), [auth.accessToken])
  const isAuthenticated = Boolean(auth.accessToken && auth.refreshToken)

  const setProfileName = (name: string) => {
    setAuth((prev) => {
      const next = { ...prev, profileName: name }
      window.localStorage.setItem('es_profile_name', name)
      return next
    })
  }

  const updateTokens = (tokens: AuthTokens, profileName?: string) => {
    window.localStorage.setItem('es_access_token', tokens.accessToken)
    window.localStorage.setItem('es_refresh_token', tokens.refreshToken)
    if (profileName !== undefined) {
      window.localStorage.setItem('es_profile_name', profileName)
    }
    setAuth((prev) => ({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      profileName: profileName ?? prev.profileName,
    }))
  }

  const clearAuth = () => {
    window.localStorage.removeItem('es_access_token')
    window.localStorage.removeItem('es_refresh_token')
    window.localStorage.removeItem('es_profile_name')
    setAuth({ accessToken: null, refreshToken: null, profileName: '' })
  }

  const login = async (email: string, password: string) => {
    const response = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
    if (!response.ok) {
      throw new Error(await readErrorMessage(response))
    }
    const tokens = (await response.json()) as AuthTokens
    updateTokens(tokens)
  }

  const register = async (payload: RegisterPayload) => {
    const response = await fetch(`${API_BASE}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    if (!response.ok) {
      throw new Error(await readErrorMessage(response))
    }
    const tokens = (await response.json()) as AuthTokens
    updateTokens(tokens, payload.fullName)
  }

  const refreshTokens = async () => {
    if (!auth.refreshToken) {
      return null
    }
    const refreshToken = auth.refreshToken
    if (refreshRef.current) {
      return refreshRef.current
    }
    const refreshPromise = (async () => {
      const refreshResponse = await fetch(`${API_BASE}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'X-Refresh-Token': refreshToken },
      })

      if (!refreshResponse.ok) {
        clearAuth()
        return null
      }

      const tokens = (await refreshResponse.json()) as AuthTokens
      updateTokens(tokens)
      return tokens
    })()

    refreshRef.current = refreshPromise
    try {
      return await refreshPromise
    } finally {
      refreshRef.current = null
    }
  }

  const logout = async () => {
    if (auth.refreshToken) {
      await fetch(`${API_BASE}/api/auth/logout`, {
        method: 'POST',
        headers: { 'X-Refresh-Token': auth.refreshToken },
      })
    }
    clearAuth()
    navigate('/')
  }

  const authFetch = async (path: string, options: RequestInit = {}) => {
    let accessToken = auth.accessToken
    if (isTokenExpired(accessToken)) {
      const refreshed = await refreshTokens()
      accessToken = refreshed?.accessToken ?? null
    }

    const headers = new Headers(options.headers)
    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }
    const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
    if (response.status !== 401 || !auth.refreshToken) {
      return response
    }

    const tokens = await refreshTokens()
    if (!tokens) {
      return response
    }

    const retryHeaders = new Headers(options.headers)
    retryHeaders.set('Authorization', `Bearer ${tokens.accessToken}`)
    return fetch(`${API_BASE}${path}`, { ...options, headers: retryHeaders })
  }

  const authValue: AuthContextValue = {
    auth,
    claims,
    isAuthenticated,
    profileName: auth.profileName,
    setProfileName,
    login,
    register,
    logout,
    authFetch,
  }

  return (
    <AuthContext.Provider value={authValue}>
      <div className="app">
        <header className="nav">
          <div className="nav-brand">
            <span className="logo-badge">ES</span>
            <div>
              <p className="brand-title">EventSphere</p>
              <p className="brand-tag">Feel the hype. Own the moment.</p>
            </div>
          </div>
          <nav className="nav-links">
            <a className="nav-link" href="#/">
              Browse
            </a>
            <a className="nav-link" href="#/tickets">
              My Tickets
            </a>
            <a className="nav-link" href="#/organizer">
              Organizer
            </a>
            {isAuthenticated ? (
              <button className="ghost-button" onClick={logout} type="button">
                Logout
              </button>
            ) : (
              <a className="ghost-button" href="#/login">
                Sign in
              </a>
            )}
          </nav>
        </header>

        <main className="page">
          {route.name === 'home' && <HomePage />}
          {route.name === 'event' && <EventDetailPage eventId={route.id} />}
          {route.name === 'login' && <LoginPage />}
          {route.name === 'register' && <RegisterPage />}
          {route.name === 'organizer' && <OrganizerHomePage />}
          {route.name === 'organizer-new' && <EventFormPage mode="create" />}
          {route.name === 'organizer-edit' && (
            <EventFormPage mode="edit" eventId={route.id} />
          )}
          {route.name === 'tickets' && <ComingSoonPage />}
        </main>

        <footer className="footer">
          <div>
            <p className="footer-title">EventSphere</p>
            <p className="footer-copy">
              Portfolio build • Ticketing that feels like a festival.
            </p>
          </div>
          <div className="footer-links">
            <span>Made with Java + React</span>
            <span>•</span>
            <span>Stripe-ready</span>
            <span>•</span>
            <span>Async by design</span>
          </div>
        </footer>
      </div>
    </AuthContext.Provider>
  )
}

function HomePage() {
  const [events, setEvents] = useState<EventResponse[]>([])
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle')
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(9)
  const [from, setFrom] = useState('')
  const [totalPages, setTotalPages] = useState(0)
  const [draftFrom, setDraftFrom] = useState('')
  const [draftSize, setDraftSize] = useState(9)

  useEffect(() => {
    const fetchEvents = async () => {
      setStatus('loading')
      setError(null)

      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
      })
      if (from) {
        params.set('from', from)
      }

      try {
        const response = await fetch(`${API_BASE}/api/events?${params}`)
        if (!response.ok) {
          throw new Error(await readErrorMessage(response))
        }
        const payload = (await response.json()) as PageResponse<EventResponse>
        setEvents(payload.content ?? [])
        setTotalPages(payload.totalPages ?? 0)
        setStatus('idle')
      } catch (err) {
        setStatus('error')
        setError(err instanceof Error ? err.message : 'Failed to load events')
      }
    }

    fetchEvents()
  }, [page, size, from])

  const applyFilters = () => {
    setPage(0)
    setSize(draftSize)
    setFrom(draftFrom)
  }

  const resetFilters = () => {
    setPage(0)
    setSize(9)
    setFrom('')
    setDraftFrom('')
    setDraftSize(9)
  }

  return (
    <div className="stack">
      <section className="hero-card">
        <div className="hero-content">
          <span className="pill">Live now</span>
          <h1>Discover events that spark your weekend</h1>
          <p>
            Browse upcoming concerts, community meetups, and curated experiences.
            Pay later — you only need a spot to reserve your vibe.
          </p>
          <div className="hero-actions">
            <button
              className="primary-button"
              onClick={() => navigate('/organizer')}
              type="button"
            >
              Host an event
            </button>
            <a className="ghost-button" href="#/tickets">
              My tickets
            </a>
          </div>
        </div>
        <div className="hero-visual">
          <img src={heroImg} alt="Event crowd" />
          <div className="hero-glow"></div>
        </div>
      </section>

      <section className="section">
        <div className="section-header">
          <div>
            <h2>Browse events</h2>
            <p className="muted">
              Filter by date and size to match your calendar.
            </p>
          </div>
          <div className="filters">
            <label className="field">
              <span>From</span>
              <input
                type="datetime-local"
                value={draftFrom}
                onChange={(event) => setDraftFrom(event.target.value)}
              />
            </label>
            <label className="field">
              <span>Cards</span>
              <select
                value={draftSize}
                onChange={(event) => setDraftSize(Number(event.target.value))}
              >
                <option value={6}>6</option>
                <option value={9}>9</option>
                <option value={12}>12</option>
              </select>
            </label>
            <button className="primary-button" onClick={applyFilters} type="button">
              Apply
            </button>
            <button className="ghost-button" onClick={resetFilters} type="button">
              Reset
            </button>
          </div>
        </div>

        {status === 'loading' && <LoadingState label="Loading events..." />}
        {status === 'error' && <ErrorState message={error ?? ''} />}
        {status === 'idle' && events.length === 0 && (
          <EmptyState label="No events match your filters yet." />
        )}

        <div className="event-grid">
          {events.map((event) => (
            <EventCard key={event.id} event={event}>
              <button
                className="secondary-button"
                onClick={() => navigate(`/events/${event.id}`)}
                type="button"
              >
                View details
              </button>
            </EventCard>
          ))}
        </div>

        <div className="pagination">
          <button
            className="ghost-button"
            onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
            disabled={page === 0}
            type="button"
          >
            Previous
          </button>
          <span className="muted">
            Page {page + 1} of {Math.max(totalPages, 1)}
          </span>
          <button
            className="ghost-button"
            onClick={() => setPage((prev) => prev + 1)}
            disabled={page + 1 >= totalPages}
            type="button"
          >
            Next
          </button>
        </div>
      </section>
    </div>
  )
}

function EventDetailPage({ eventId }: { eventId: string }) {
  const [event, setEvent] = useState<EventResponse | null>(null)
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchEvent = async () => {
      setStatus('loading')
      setError(null)
      try {
        const response = await fetch(`${API_BASE}/api/events/${eventId}`)
        if (!response.ok) {
          throw new Error(await readErrorMessage(response))
        }
        const payload = (await response.json()) as EventResponse
        setEvent(payload)
        setStatus('idle')
      } catch (err) {
        setStatus('error')
        setError(err instanceof Error ? err.message : 'Failed to load event')
      }
    }

    fetchEvent()
  }, [eventId])

  if (status === 'loading') {
    return <LoadingState label="Loading event..." />
  }

  if (status === 'error') {
    return <ErrorState message={error ?? ''} />
  }

  if (!event) {
    return <EmptyState label="Event not found." />
  }

  return (
    <div className="stack">
      <button className="ghost-button" onClick={() => navigate('/')} type="button">
        ← Back to events
      </button>
      <section className="detail-card">
        <div className="detail-image">
          <img src={event.imageUrl || heroImg} alt={event.title} />
          <span className="status-pill">{event.status}</span>
        </div>
        <div className="detail-content">
          <h1>{event.title}</h1>
          <p className="muted">{event.description || 'No description yet.'}</p>
          <div className="detail-grid">
            <div>
              <p className="label">Date</p>
              <p>{formatDate(event.eventDate)}</p>
            </div>
            <div>
              <p className="label">Location</p>
              <p>{event.location}</p>
            </div>
            <div>
              <p className="label">Organizer</p>
              <p>{event.organizerName}</p>
            </div>
          </div>
          <div className="detail-actions">
            <button className="primary-button" type="button">
              Buy tickets (coming soon)
            </button>
            <a className="ghost-button" href="#/tickets">
              View my tickets
            </a>
          </div>
        </div>
      </section>
    </div>
  )
}

function LoginPage() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState<'idle' | 'loading'>('idle')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setStatus('loading')
    setError(null)
    try {
      await login(email, password)
      navigate('/organizer')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to sign in')
    } finally {
      setStatus('idle')
    }
  }

  return (
    <section className="auth-card">
      <div>
        <h2>Welcome back</h2>
        <p className="muted">Sign in to manage your organizer dashboard.</p>
      </div>
      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="primary-button" type="submit" disabled={status === 'loading'}>
          {status === 'loading' ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
      <p className="muted">
        New here? <a href="#/register">Create an account</a>
      </p>
    </section>
  )
}

function RegisterPage() {
  const { register } = useAuth()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState<'idle' | 'loading'>('idle')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setStatus('loading')
    setError(null)
    try {
      await register({ email, password, fullName })
      navigate('/organizer')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to register')
    } finally {
      setStatus('idle')
    }
  }

  return (
    <section className="auth-card">
      <div>
        <h2>Create your organizer profile</h2>
        <p className="muted">We will use your name to filter your events.</p>
      </div>
      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Full name</span>
          <input
            type="text"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="primary-button" type="submit" disabled={status === 'loading'}>
          {status === 'loading' ? 'Creating...' : 'Create account'}
        </button>
      </form>
      <p className="muted">
        Already have an account? <a href="#/login">Sign in</a>
      </p>
    </section>
  )
}

function OrganizerHomePage() {
  const { isAuthenticated, claims, profileName, setProfileName, authFetch } =
    useAuth()
  const [events, setEvents] = useState<EventResponse[]>([])
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle')
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [filterName, setFilterName] = useState(profileName)

  useEffect(() => {
    const fetchEvents = async () => {
      setStatus('loading')
      setError(null)
      try {
        const response = await fetch(`${API_BASE}/api/events?page=0&size=50`)
        if (!response.ok) {
          throw new Error(await readErrorMessage(response))
        }
        const payload = (await response.json()) as PageResponse<EventResponse>
        setEvents(payload.content ?? [])
        setStatus('idle')
      } catch (err) {
        setStatus('error')
        setError(err instanceof Error ? err.message : 'Unable to load events')
      }
    }

    fetchEvents()
  }, [refreshKey])

  useEffect(() => {
    setFilterName(profileName)
  }, [profileName])

  const filtered = events.filter((event) => {
    if (!filterName.trim()) {
      return true
    }
    return event.organizerName
      .toLowerCase()
      .includes(filterName.trim().toLowerCase())
  })

  const handleCancel = async (eventId: string) => {
    if (!window.confirm('Cancel this event?')) {
      return
    }
    const response = await authFetch(`/api/events/${eventId}`, {
      method: 'DELETE',
    })
    if (!response.ok) {
      setError(await readErrorMessage(response))
      return
    }
    setRefreshKey((prev) => prev + 1)
  }

  if (!isAuthenticated) {
    return (
      <section className="empty-card">
        <h2>Organizer dashboard</h2>
        <p className="muted">
          Sign in to create and manage events. Your access token will unlock
          organizer tools.
        </p>
        <a className="primary-button" href="#/login">
          Sign in
        </a>
      </section>
    )
  }

  return (
    <div className="stack">
      <section className="organizer-header">
        <div>
          <h2>Organizer dashboard</h2>
          <p className="muted">
            Role: {claims?.role ?? 'USER'} • Email: {claims?.sub ?? 'unknown'}
          </p>
        </div>
        <button
          className="primary-button"
          onClick={() => navigate('/organizer/events/new')}
          type="button"
        >
          Create new event
        </button>
      </section>

      <section className="section card">
        <div className="section-header">
          <div>
            <h3>My events</h3>
            <p className="muted">
              Filter the list by organizer name until your dedicated endpoint
              arrives.
            </p>
          </div>
          <div className="filter-controls">
            <label className="field">
              <span>Organizer name</span>
              <input
                type="text"
                value={filterName}
                onChange={(event) => setFilterName(event.target.value)}
                placeholder="e.g. Alex Carter"
              />
            </label>
            <button
              className="ghost-button"
              type="button"
              onClick={() => setProfileName(filterName)}
            >
              Save name
            </button>
          </div>
        </div>

        {status === 'loading' && <LoadingState label="Loading your events..." />}
        {status === 'error' && <ErrorState message={error ?? ''} />}
        {status === 'idle' && filtered.length === 0 && (
          <EmptyState label="No events match your organizer name yet." />
        )}

        <div className="event-grid">
          {filtered.map((event) => (
            <EventCard key={event.id} event={event}>
              <button
                className="ghost-button"
                onClick={() => navigate(`/organizer/events/${event.id}/edit`)}
                type="button"
              >
                Edit
              </button>
              <button
                className="danger-button"
                onClick={() => handleCancel(event.id)}
                type="button"
              >
                Cancel
              </button>
            </EventCard>
          ))}
        </div>
      </section>
    </div>
  )
}

function EventFormPage({
  mode,
  eventId,
}: {
  mode: 'create' | 'edit'
  eventId?: string
}) {
  const { authFetch } = useAuth()
  const [status, setStatus] = useState<'idle' | 'loading'>('idle')
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    title: '',
    description: '',
    location: '',
    eventDate: '',
    imageUrl: '',
  })

  useEffect(() => {
    if (mode !== 'edit' || !eventId) {
      return
    }

    const fetchEvent = async () => {
      setStatus('loading')
      try {
        const response = await fetch(`${API_BASE}/api/events/${eventId}`)
        if (!response.ok) {
          throw new Error(await readErrorMessage(response))
        }
        const payload = (await response.json()) as EventResponse
        setForm({
          title: payload.title,
          description: payload.description ?? '',
          location: payload.location,
          eventDate: toDateTimeInput(payload.eventDate),
          imageUrl: payload.imageUrl ?? '',
        })
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unable to load event')
      } finally {
        setStatus('idle')
      }
    }

    fetchEvent()
  }, [eventId, mode])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setStatus('loading')
    setError(null)
    const payload = {
      title: form.title,
      description: form.description || null,
      location: form.location,
      eventDate: form.eventDate,
      imageUrl: form.imageUrl || null,
    }

    const response = await authFetch(
      mode === 'edit' ? `/api/events/${eventId}` : '/api/events',
      {
        method: mode === 'edit' ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      },
    )

    if (!response.ok) {
      setError(await readErrorMessage(response))
      setStatus('idle')
      return
    }

    navigate('/organizer')
  }

  return (
    <section className="form-card">
      <div>
        <h2>{mode === 'edit' ? 'Edit event' : 'Create event'}</h2>
        <p className="muted">
          {mode === 'edit'
            ? 'Refresh your event details before it goes live.'
            : 'Launch a new event and start selling tickets.'}
        </p>
      </div>
      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Title</span>
          <input
            type="text"
            value={form.title}
            onChange={(event) =>
              setForm((prev) => ({ ...prev, title: event.target.value }))
            }
            required
          />
        </label>
        <label className="field">
          <span>Description</span>
          <textarea
            rows={4}
            value={form.description}
            onChange={(event) =>
              setForm((prev) => ({ ...prev, description: event.target.value }))
            }
          />
        </label>
        <div className="form-grid">
          <label className="field">
            <span>Location</span>
            <input
              type="text"
              value={form.location}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, location: event.target.value }))
              }
              required
            />
          </label>
          <label className="field">
            <span>Date & time</span>
            <input
              type="datetime-local"
              value={form.eventDate}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, eventDate: event.target.value }))
              }
              required
            />
          </label>
        </div>
        <label className="field">
          <span>Image URL</span>
          <input
            type="url"
            value={form.imageUrl}
            onChange={(event) =>
              setForm((prev) => ({ ...prev, imageUrl: event.target.value }))
            }
            placeholder="https://"
          />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button
            className="primary-button"
            type="submit"
            disabled={status === 'loading'}
          >
            {status === 'loading'
              ? mode === 'edit'
                ? 'Saving...'
                : 'Creating...'
              : mode === 'edit'
                ? 'Save changes'
                : 'Create event'}
          </button>
          <button
            className="ghost-button"
            type="button"
            onClick={() => navigate('/organizer')}
          >
            Cancel
          </button>
        </div>
      </form>
    </section>
  )
}

function ComingSoonPage() {
  return (
    <section className="empty-card">
      <span className="pill">Coming soon</span>
      <h2>Tickets & QR check-in</h2>
      <p className="muted">
        The purchase flow, QR codes, and PDF downloads will light up when the
        remaining backend endpoints land.
      </p>
      <a className="primary-button" href="#/">
        Back to events
      </a>
    </section>
  )
}

function EventCard({
  event,
  children,
}: {
  event: EventResponse
  children: ReactNode
}) {
  return (
    <article className="event-card">
      <div className="event-image">
        <img src={event.imageUrl || heroImg} alt={event.title} />
      </div>
      <div className="event-body">
        <div className="event-meta">
          <span className="pill muted">Hosted by {event.organizerName}</span>
          <span className="status-pill">{event.status}</span>
        </div>
        <h3>{event.title}</h3>
        <p className="muted">{event.description || 'No description yet.'}</p>
        <div className="event-details">
          <div>
            <p className="label">Date</p>
            <p>{formatDate(event.eventDate)}</p>
          </div>
          <div>
            <p className="label">Location</p>
            <p>{event.location}</p>
          </div>
        </div>
      </div>
      <div className="event-actions">{children}</div>
    </article>
  )
}

function LoadingState({ label }: { label: string }) {
  return <p className="status">{label}</p>
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="status error">
      <p>{message || 'Something went wrong.'}</p>
    </div>
  )
}

function EmptyState({ label }: { label: string }) {
  return (
    <div className="status">
      <p>{label}</p>
    </div>
  )
}

function useHashRoute(): Route {
  const [route, setRoute] = useState<Route>(() =>
    parseRoute(window.location.hash),
  )

  useEffect(() => {
    const handler = () => setRoute(parseRoute(window.location.hash))
    window.addEventListener('hashchange', handler)
    return () => window.removeEventListener('hashchange', handler)
  }, [])

  return route
}

function parseRoute(hash: string): Route {
  const path = hash.replace('#', '') || '/'
  const segments = path.split('/').filter(Boolean)

  if (segments.length === 0) {
    return { name: 'home' }
  }

  if (segments[0] === 'events' && segments[1]) {
    return { name: 'event', id: segments[1] }
  }

  if (segments[0] === 'login') {
    return { name: 'login' }
  }

  if (segments[0] === 'register') {
    return { name: 'register' }
  }

  if (segments[0] === 'tickets') {
    return { name: 'tickets' }
  }

  if (segments[0] === 'organizer') {
    if (segments[1] === 'events' && segments[2] === 'new') {
      return { name: 'organizer-new' }
    }
    if (segments[1] === 'events' && segments[2] && segments[3] === 'edit') {
      return { name: 'organizer-edit', id: segments[2] }
    }
    return { name: 'organizer' }
  }

  return { name: 'home' }
}

function navigate(path: string) {
  window.location.hash = path
}

function loadAuth(): AuthState {
  return {
    accessToken: window.localStorage.getItem('es_access_token'),
    refreshToken: window.localStorage.getItem('es_refresh_token'),
    profileName: window.localStorage.getItem('es_profile_name') ?? '',
  }
}

function decodeJwt(token: string | null): JwtClaims | null {
  if (!token) {
    return null
  }
  try {
    const payload = token.split('.')[1]
    if (!payload) {
      return null
    }
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const decoded = JSON.parse(atob(base64))
    return decoded as JwtClaims
  } catch {
    return null
  }
}

function isTokenExpired(token: string | null) {
  if (!token) {
    return true
  }
  const claims = decodeJwt(token)
  if (!claims?.exp) {
    return true
  }
  return Date.now() >= claims.exp * 1000
}

function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('Auth context is missing')
  }
  return context
}

function formatDate(value: string | undefined) {
  if (!value) {
    return 'TBA'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'Invalid date'
  }
  return dateFormatter.format(date)
}

function toDateTimeInput(value?: string) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (amount: number) => amount.toString().padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function readErrorMessage(response: Response) {
  try {
    const data = (await response.json()) as
      | { message?: string; error?: string }
      | string
    if (typeof data === 'string') {
      return data
    }
    return data?.message ?? data?.error ?? response.statusText
  } catch {
    return response.statusText
  }
}

export default App
