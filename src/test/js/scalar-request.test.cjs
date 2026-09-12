const { readFileSync } = require('node:fs')
const { webcrypto } = require('node:crypto')
const { runInNewContext } = require('node:vm')
const assert = require('node:assert/strict')
const { test } = require('node:test')

test('POST/PUT/PATCH resolve UUID tokens per send and preserve fixed values and files', () => {
  const html = readFileSync(new URL('../../main/resources/static/docs/index.html', `file://${__dirname}/`), 'utf8')
  let beforeRequest
  runInNewContext(html.match(/<script>([\s\S]*?)<\/script>/)[1], {
    crypto: { getRandomValues: (bytes) => webcrypto.getRandomValues(bytes) },
    Scalar: { createApiReference: (_, config) => { beforeRequest = config.onBeforeRequest } },
  })
  const token = '{{$randomUUID}}'
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
  const generated = new Set()
  const remember = (value) => {
    assert.match(value, uuid)
    assert.ok(!generated.has(value), 'each substitution needs a new UUID')
    generated.add(value)
  }

  for (const method of ['POST', 'PUT', 'PATCH']) {
    const template = JSON.stringify({ name: token, nested: [{ id: token }], email: `user-${token}@example.com`, fixed: 'keep', count: 2, enabled: true, empty: null })
    for (let send = 0; send < 2; send++) {
      const requestBuilder = { method, path: { raw: '/another-api' }, body: { mode: 'raw', value: template } }
      beforeRequest({ requestBuilder })
      const body = JSON.parse(requestBuilder.body.value)
      remember(body.name)
      remember(body.nested[0].id)
      remember(body.email.slice(5, -12))
      assert.equal(body.fixed, 'keep')
      assert.equal(body.count, 2)
      assert.equal(body.enabled, true)
      assert.equal(body.empty, null)
    }
    for (const mode of ['formdata', 'urlencoded']) {
      const file = new Blob([token])
      const requestBuilder = { method, body: { mode, value: [{ key: 'name', value: token }, { key: 'fixed', value: 'keep' }, { key: 'file', value: file }] } }
      beforeRequest({ requestBuilder })
      remember(requestBuilder.body.value[0].value)
      assert.equal(requestBuilder.body.value[1].value, 'keep')
      assert.equal(requestBuilder.body.value[2].value, file)
    }
    beforeRequest({ requestBuilder: { method, body: null } })
  }
  for (const method of ['GET', 'DELETE']) {
    const requestBuilder = { method, body: { mode: 'raw', value: token } }
    beforeRequest({ requestBuilder })
    assert.equal(requestBuilder.body.value, token)
  }
})
