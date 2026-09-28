import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHmac, scryptSync, timingSafeEqual } from 'node:crypto';

const root = fileURLToPath(new URL('.', import.meta.url));
const pub = join(root, 'public');
try {
  const env = await readFile(join(root, '.env'), 'utf8');
  for (const line of env.split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (match && !process.env[match[1]]) process.env[match[1]] = match[2].replace(/^['"]|['"]$/g, '');
  }
} catch {}

const token = process.env.AIRTABLE_TOKEN;
const base = process.env.AIRTABLE_BASE_ID || 'appj4RydYWjdUuD5o';
const passwordHash = process.env.APP_PASSWORD_HASH;
const passwordSalt = process.env.APP_PASSWORD_SALT;
const sessionSecret = process.env.APP_SESSION_SECRET;
const authConfigured = Boolean(passwordHash && passwordSalt && sessionSecret);
const port = Number(process.env.PORT || 4173);
const host = process.env.HOST || (process.env.NODE_ENV === 'production' ? '0.0.0.0' : '127.0.0.1');
const tables = { lancamentos: 'Lançamentos', categorias: 'Categorias', contas: 'Contas', cartoes: 'Cartões' };
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/manifest+json; charset=utf-8', '.webmanifest': 'application/manifest+json; charset=utf-8', '.svg': 'image/svg+xml' };
const respond = (res, status, value) => {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify(value));
};
async function body(req) {
  let raw = '';
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 16_384) throw Object.assign(new Error('Solicitação muito grande.'), { status: 413 });
  }
  return raw ? JSON.parse(raw) : {};
}
function cookieValue(req, name) {
  const part = (req.headers.cookie || '').split(';').map(v => v.trim()).find(v => v.startsWith(name + '='));
  return part ? decodeURIComponent(part.slice(name.length + 1)) : '';
}
function signature(payload) {
  return createHmac('sha256', sessionSecret).update(payload).digest('base64url');
}
function isAuthenticated(req) {
  if (!authConfigured) return false;
  const secure = process.env.NODE_ENV === 'production';
  const raw = cookieValue(req, secure ? '__Host-finance_session' : 'finance_session');
  const [expires, supplied, extra] = raw.split('.');
  if (!expires || !supplied || extra || !/^\d+$/.test(expires) || Number(expires) < Date.now()) return false;
  const expected = Buffer.from(signature(expires));
  const actual = Buffer.from(supplied);
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
const loginFailures = new Map();
function rateLimitKey(req) {
  return String(req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown').split(',')[0].trim();
}
async function list(table) {
  if (!token) throw Object.assign(new Error('Configure AIRTABLE_TOKEN no servidor.'), { status: 503 });
  let records = [], offset;
  do {
    const url = new URL(`https://api.airtable.com/v0/${base}/${encodeURIComponent(tables[table])}`);
    url.searchParams.set('pageSize', '100');
    if (offset) url.searchParams.set('offset', offset);
    const response = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
    const data = await response.json();
    if (!response.ok) throw Object.assign(new Error(data?.error?.message || 'Falha ao consultar o Airtable.'), { status: response.status });
    records.push(...(data.records || []));
    offset = data.offset;
  } while (offset && records.length < 2000);
  return records;
}
const app = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const isLoginPage = req.method === 'GET' && url.pathname === '/login.html';
    const isLoginApi = req.method === 'POST' && url.pathname === '/api/login';
    if (isLoginApi) {
      if (!authConfigured) return respond(res, 503, { error: 'Autenticação ainda não está configurada.' });
      const key = rateLimitKey(req);
      const now = Date.now();
      const state = loginFailures.get(key);
      if (state && state.blockedUntil > now) return respond(res, 429, { error: 'Muitas tentativas. Aguarde 15 minutos.' });
      const { password } = await body(req);
      const candidate = typeof password === 'string' ? scryptSync(password, passwordSalt, 64) : Buffer.alloc(64);
      const expected = Buffer.from(passwordHash, 'hex');
      const valid = expected.length === candidate.length && timingSafeEqual(candidate, expected);
      if (!valid) {
        const failures = (state?.failures || 0) + 1;
        loginFailures.set(key, { failures, blockedUntil: failures >= 6 ? now + 15 * 60_000 : 0 });
        return respond(res, 401, { error: 'Senha incorreta.' });
      }
      loginFailures.delete(key);
      const expires = String(now + 12 * 60 * 60_000);
      const cookieName = process.env.NODE_ENV === 'production' ? '__Host-finance_session' : 'finance_session';
      const secure = req.headers['x-forwarded-proto'] === 'https' || process.env.NODE_ENV === 'production' ? '; Secure' : '';
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Set-Cookie': `${cookieName}=${expires}.${signature(expires)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=43200${secure}` });
      return res.end(JSON.stringify({ ok: true }));
    }
    if (!isLoginPage && !isAuthenticated(req)) {
      if (url.pathname.startsWith('/api/')) return respond(res, 401, { error: 'Acesso restrito. Entre para continuar.' });
      res.writeHead(302, { Location: '/login.html', 'Cache-Control': 'no-store' });
      return res.end();
    }
    if (isLoginPage && isAuthenticated(req)) {
      res.writeHead(302, { Location: '/', 'Cache-Control': 'no-store' });
      return res.end();
    }
    if (req.method === 'POST' && url.pathname === '/api/logout') {
      const cookieName = process.env.NODE_ENV === 'production' ? '__Host-finance_session' : 'finance_session';
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Set-Cookie': `${cookieName}=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0${process.env.NODE_ENV === 'production' ? '; Secure' : ''}` });
      return res.end(JSON.stringify({ ok: true }));
    }
    if (url.pathname.startsWith('/api/')) {
      if (req.method === 'GET' && url.pathname === '/api/status') return respond(res, 200, { connected: Boolean(token) });
      if (req.method === 'GET' && url.pathname === '/api/bootstrap') {
        const [lancamentos, categorias, contas, cartoes] = await Promise.all(Object.keys(tables).map(list));
        return respond(res, 200, { lancamentos, categorias, contas, cartoes });
      }
      if (req.method === 'POST' && url.pathname === '/api/mutate') {
        if (!token) throw Object.assign(new Error('Configure AIRTABLE_TOKEN no servidor.'), { status: 503 });
        const { action, table, id, fields } = await body(req);
        if (!tables[table]) return respond(res, 400, { error: 'Tabela inválida.' });
        let method, recordId = id || '';
        if (action === 'create' && fields) method = 'POST';
        else if (action === 'update' && id && fields) method = 'PATCH';
        else if (action === 'delete' && id) method = 'DELETE';
        else return respond(res, 400, { error: 'Operação inválida.' });
        const endpoint = `https://api.airtable.com/v0/${base}/${encodeURIComponent(tables[table])}${recordId ? `/${encodeURIComponent(recordId)}` : ''}`;
        const response = await fetch(endpoint, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...(fields && method !== 'DELETE' ? { body: JSON.stringify({ fields }) } : {}) });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) return respond(res, response.status, { error: data?.error?.message || 'Falha ao alterar o Airtable.' });
        return respond(res, 200, data);
      }
      return respond(res, 404, { error: 'Rota não encontrada.' });
    }
    if (isLoginPage) {
      const login = await readFile(join(pub, 'login.html'));
      res.writeHead(200, { 'Content-Type': mime['.html'], 'Cache-Control': 'no-store', 'X-Frame-Options': 'DENY', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' });
      return res.end(login);
    }
    const requestedPath = url.pathname === '/' ? '/index.html' : decodeURIComponent(url.pathname);
    const path = normalize(join(pub, requestedPath.slice(1)));
    if (!path.startsWith(pub + sep)) return respond(res, 403, { error: 'Acesso negado.' });
    const file = await readFile(path);
    res.writeHead(200, { 'Content-Type': mime[extname(path)] || 'application/octet-stream', 'Cache-Control': 'no-store', 'X-Frame-Options': 'DENY', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' });
    res.end(file);
  } catch (error) {
    respond(res, error.status || 500, { error: error.message || 'Erro inesperado.' });
  }
});
app.listen(port, host, () => console.log(`Gestor Financeiro Android/PWA em http://${host}:${port}`));

