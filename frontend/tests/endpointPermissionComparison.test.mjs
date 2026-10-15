import assert from 'node:assert/strict'
import test from 'node:test'
import {
  compareEndpointPermissions,
  filterEndpointPermissionComparisons,
} from '../src/permissions/compareEndpointPermissions.ts'

test('matches approved wildcard policies to concrete controller paths', () => {
  const [comparison] = compareEndpointPermissions([
    {
      method: 'GET',
      path: '/api/rooms/{roomId}/history',
      access: 'ROLES',
      roles: ['ADMIN', 'OWNER', 'RECEPTIONIST', 'HOUSEKEEPING'],
    },
  ])

  assert.equal(comparison.hasMismatch, false)
  assert.deepEqual(comparison.approved.roles, [
    'ADMIN',
    'OWNER',
    'RECEPTIONIST',
    'HOUSEKEEPING',
  ])
})

test('flags role or access-level differences from the approved matrix', () => {
  const comparisons = compareEndpointPermissions([
    {
      method: 'GET',
      path: '/api/rooms/calendar',
      access: 'ROLES',
      roles: ['ADMIN', 'RECEPTIONIST'],
    },
    {
      method: 'GET',
      path: '/api/public/rooms/search',
      access: 'AUTHENTICATED',
      roles: ['ADMIN', 'OWNER', 'RECEPTIONIST', 'HOUSEKEEPING'],
    },
  ])

  assert.deepEqual(comparisons.map(({ hasMismatch }) => hasMismatch), [true, true])
})

test('treats unapproved API paths as denied', () => {
  const [comparison] = compareEndpointPermissions([
    {
      method: 'GET',
      path: '/api/not-registered',
      access: 'DENIED',
      roles: [],
    },
  ])

  assert.equal(comparison.hasMismatch, false)
  assert.deepEqual(comparison.approved.roles, [])
})

test('mismatch filter excludes matched endpoints and retains discrepancies', () => {
  const comparisons = compareEndpointPermissions([
    {
      method: 'GET',
      path: '/api/rooms/calendar',
      access: 'ROLES',
      roles: ['RECEPTIONIST'],
    },
    {
      method: 'GET',
      path: '/api/rooms/calendar',
      access: 'ROLES',
      roles: ['ADMIN', 'RECEPTIONIST'],
    },
  ])
  const filtered = filterEndpointPermissionComparisons(comparisons, {
    mismatchesOnly: true,
    role: 'ALL',
    query: '',
  })

  assert.equal(filtered.length, 1)
  assert.equal(filtered[0].hasMismatch, true)
})
test('occupancy summary, detailed report, and CSV endpoints are owner-only', () => {
  const endpoints = [
    '/api/reports/occupancy',
    '/api/reports/occupancy/detailed',
    '/api/reports/occupancy.csv',
  ].map(path => ({ method: 'GET', path, access: 'ROLES', roles: ['OWNER'] }))
  const comparisons = compareEndpointPermissions(endpoints)
  assert.ok(comparisons.every(({ hasMismatch }) => !hasMismatch))
})
