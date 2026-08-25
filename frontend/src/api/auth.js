import http from './http'

export async function login(credentials) {
  return (await http.post('/auth/login', credentials)).data
}

export async function getCurrentUser() {
  return (await http.get('/auth/me')).data
}
