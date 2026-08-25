import http from './http'

export async function getStations(status) {
  return (await http.get('/stations', { params: status ? { status } : undefined })).data
}

export async function createStation(payload) {
  return (await http.post('/admin/stations', payload)).data
}

export async function updateStation(stationId, payload) {
  return (await http.put(`/admin/stations/${stationId}`, payload)).data
}

export async function updateStationStatus(stationId, status) {
  return (await http.patch(`/admin/stations/${stationId}/status`, { status })).data
}

export async function getLockerGrid(stationId) {
  return (await http.get(`/stations/${stationId}/grid`)).data
}

export async function createLockerCell(stationId, payload) {
  return (await http.post(`/admin/stations/${stationId}/cells`, payload)).data
}

export async function updateLockerCellStatus(stationId, cellId, status) {
  return (await http.patch(`/admin/stations/${stationId}/cells/${cellId}/status`, { status })).data
}
