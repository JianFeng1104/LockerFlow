import http from './http'

export async function getCourierParcels() {
  return (await http.get('/courier/parcels')).data
}

export async function getCustomerParcels() {
  return (await http.get('/customer/parcels')).data
}

export async function storeParcel(payload) {
  return (await http.post('/courier/parcels', payload)).data
}

export async function pickupParcel(parcelId, pickupCode) {
  return (await http.post(`/customer/parcels/${parcelId}/pickup`, { pickupCode })).data
}
