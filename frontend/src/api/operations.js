import http from './http'

export async function runExpirationProcessing() {
  return (await http.post('/admin/operations/expiration/run')).data
}
