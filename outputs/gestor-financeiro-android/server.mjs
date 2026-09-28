import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=fileURLToPath(new URL('.',import.meta.url));
const pub=join(root,'public');
try{const env=await readFile(join(root,'.env'),'utf8');for(const line of env.split(/\r?\n/)){const m=line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);if(m&&!process.env[m[1]])process.env[m[1]]=m[2].replace(/^['"]|['"]$/g,'');}}catch{}
const token=process.env.AIRTABLE_TOKEN;
const base=process.env.AIRTABLE_BASE_ID||'appj4RydYWjdUuD5o';
const port=Number(process.env.PORT||4173),host=process.env.HOST||(process.env.NODE_ENV==='production'?'0.0.0.0':'127.0.0.1');
const tables={lancamentos:'Lançamentos',categorias:'Categorias',contas:'Contas',cartoes:'Cartões'};
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/manifest+json; charset=utf-8','.webmanifest':'application/manifest+json; charset=utf-8','.svg':'image/svg+xml'};
const respond=(res,status,value)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(value));};
async function body(req){let raw='';for await(const chunk of req)raw+=chunk;return raw?JSON.parse(raw):{};}
async function list(table){if(!token)throw Object.assign(new Error('Configure AIRTABLE_TOKEN no arquivo .env.'),{status:503});let records=[],offset;do{const u=new URL(`https://api.airtable.com/v0/${base}/${encodeURIComponent(tables[table])}`);u.searchParams.set('pageSize','100');if(offset)u.searchParams.set('offset',offset);const r=await fetch(u,{headers:{Authorization:`Bearer ${token}`}});const data=await r.json();if(!r.ok)throw Object.assign(new Error(data?.error?.message||'Falha ao consultar o Airtable.'),{status:r.status});records.push(...(data.records||[]));offset=data.offset;}while(offset&&records.length<2000);return records;}
const app=http.createServer(async(req,res)=>{try{const url=new URL(req.url,`http://${req.headers.host||'localhost'}`);if(url.pathname.startsWith('/api/')){
 if(req.method==='GET'&&url.pathname==='/api/status')return respond(res,200,{connected:Boolean(token)});
 if(req.method==='GET'&&url.pathname==='/api/bootstrap'){const [lancamentos,categorias,contas,cartoes]=await Promise.all(Object.keys(tables).map(list));return respond(res,200,{lancamentos,categorias,contas,cartoes});}
 if(req.method==='POST'&&url.pathname==='/api/mutate'){if(!token)throw Object.assign(new Error('Configure AIRTABLE_TOKEN no arquivo .env.'),{status:503});const {action,table,id,fields}=await body(req);if(!tables[table])return respond(res,400,{error:'Tabela inválida.'});let method,recordId=id||'';if(action==='create'&&fields)method='POST';else if(action==='update'&&id&&fields)method='PATCH';else if(action==='delete'&&id)method='DELETE';else return respond(res,400,{error:'Operação inválida.'});const endpoint=`https://api.airtable.com/v0/${base}/${encodeURIComponent(tables[table])}${recordId?`/${encodeURIComponent(recordId)}`:''}`;const r=await fetch(endpoint,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},...(fields&&method!=='DELETE'?{body:JSON.stringify({fields})}:{})});const data=await r.json().catch(()=>({}));if(!r.ok)return respond(res,r.status,{error:data?.error?.message||'Falha ao alterar o Airtable.'});return respond(res,200,data);}
 return respond(res,404,{error:'Rota não encontrada.'});}
 const path=normalize(join(pub,(url.pathname==='/'?'/index.html':decodeURIComponent(url.pathname)).slice(1)));if(!path.startsWith(pub+sep))return respond(res,403,{error:'Acesso negado.'});const file=await readFile(path);res.writeHead(200,{'Content-Type':mime[extname(path)]||'application/octet-stream'});res.end(file);
}catch(error){respond(res,error.status||500,{error:error.message||'Erro inesperado.'});}});
app.listen(port,host,()=>console.log(`Gestor Financeiro Android/PWA em http://${host}:${port}`));
