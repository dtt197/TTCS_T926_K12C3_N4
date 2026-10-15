import { apiRequest } from './apiClient'
import type {
  CreateEndpointDiscrepancyPayload,
  EndpointDiscrepancyTicket,
} from '../types/endpointPermission'

const ENDPOINT = '/api/admin/security/endpoint-discrepancies'

export function getEndpointDiscrepancies() {
  return apiRequest<EndpointDiscrepancyTicket[]>(ENDPOINT)
}

export function createEndpointDiscrepancy(payload: CreateEndpointDiscrepancyPayload) {
  return apiRequest<EndpointDiscrepancyTicket>(ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
