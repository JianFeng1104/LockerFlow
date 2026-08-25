# LockerFlow Frontend

Vue 3 and Vite client for LockerFlow. Phase 9 provides login and session restoration, Bearer API integration, role-aware routes, shared application chrome, live administrator station and locker management, courier storage/history, customer parcel pickup, and frontend unit/component tests.

Business pages use live backend APIs and contain no demo parcel, station, locker, user, grid, or expiration records. Final visual/portfolio polish remains a later phase.

```bash
npm install
npm run dev
npm run test:run
npm run build
```

During development, Vite proxies `/api` to `http://localhost:8080`. The default `VITE_API_BASE_URL` is `/api`, keeping browser development requests same-origin through that proxy.
