const content=document.getElementById('content');
const nav=document.getElementById('nav');
const pageTitle=document.getElementById('page-title');
let session=null, csrf=null;

const roleSections={
  GESTOR_COMERCIAL:['dashboard','clientes'],
  ADMINISTRADOR_VENTAS:['dashboard','inventario','pedidos'],
  ESPECIALISTA_EXPORTACION:['dashboard','exportaciones'],
  COORDINADOR_LOGISTICO:['dashboard','seguimiento'],
  INSPECTOR_CALIDAD:['dashboard','calidad'],
  AUDITOR_PROCESO:['dashboard','trazabilidad']
};
const labels={dashboard:'Dashboard operativo',clientes:'Clientes internacionales',inventario:'Productos y lotes',pedidos:'Pedidos de exportación',exportaciones:'Expediente y documentación',seguimiento:'Seguimiento de exportación',calidad:'Inspecciones de calidad',trazabilidad:'Trazabilidad de lotes'};

async function request(url, options={}){
  const method=(options.method||'GET').toUpperCase();
  const headers={'Content-Type':'application/json',...(options.headers||{})};
  if(method!=='GET'&&method!=='HEAD'){
    if(!csrf) csrf=await fetch('/api/csrf').then(r=>r.json());
    headers[csrf.headerName]=csrf.token;
  }
  const res=await fetch(url,{...options,headers});
  if(res.status===401){location='/login.html';return;}
  const ct=res.headers.get('content-type')||'';
  const body=ct.includes('application/json')?await res.json():await res.text();
  if(res.status===403) throw new Error(body?.message||'Tu rol no tiene permiso para esta operación.');
  if(res.status===404) throw new Error(body?.message||'El recurso solicitado no existe.');
  if(res.status===409) throw new Error(body?.message||'La operación entra en conflicto con datos existentes.');
  if(res.status>=500) throw new Error(body?.message||'Ocurrió un error interno. Intenta nuevamente.');
  if(!res.ok) throw new Error(body?.message||body||`Error HTTP ${res.status}`);
  return body;
}
function toast(msg,error=false){const t=document.getElementById('toast');t.textContent=msg;t.className='toast show'+(error?' error':'');setTimeout(()=>t.className='toast',3000)}
function escapeHtml(v){return String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]))}
function badge(v){const x=String(v||'');const cls=/RECHAZ|CANCEL|BLOQ/.test(x)?'danger':/PEND|BORRADOR|PREPAR/.test(x)?'warn':'';return `<span class="badge ${cls}">${escapeHtml(x)}</span>`}
function table(headers,rows){return `<div class="table-wrap"><table><thead><tr>${headers.map(h=>`<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`}
function setPage(key){document.querySelectorAll('.nav-link[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page===key));pageTitle.textContent=labels[key]||key;loadPage(key).catch(e=>{content.innerHTML=`<div class="panel"><strong>Error</strong><p>${escapeHtml(e.message)}</p></div>`;toast(e.message,true)})}

async function init(){
  session=await request('/api/session'); csrf=await request('/api/csrf');
  document.getElementById('user-name').textContent=session.nombre;
  document.getElementById('role-label').textContent=session.rol.replaceAll('_',' ');
  const sections=roleSections[session.rol]||['dashboard'];
  nav.innerHTML=sections.map(k=>`<button class="nav-link" data-page="${k}">${labels[k]}</button>`).join('');
  nav.querySelectorAll('button').forEach(b=>b.onclick=()=>setPage(b.dataset.page));
  const lf=document.getElementById('logout-form');
  const hidden=document.createElement('input');hidden.type='hidden';hidden.name=csrf.parameterName;hidden.value=csrf.token;lf.appendChild(hidden);
  setPage('dashboard');
}

async function loadPage(key){
  if(key==='dashboard') return dashboard();
  if(key==='clientes') return clientes();
  if(key==='inventario') return inventario();
  if(key==='pedidos') return pedidos();
  if(key==='exportaciones') return exportaciones();
  if(key==='seguimiento') return seguimiento();
  if(key==='calidad') return calidad();
  if(key==='trazabilidad') return trazabilidad();
}

async function dashboard(){
  const [d,o]=await Promise.all([request('/api/dashboard'),request('/api/dashboard/operativo')]);
  const items=[['Clientes activos',o.clientesActivos],['Productos',d.productos],['Lotes disponibles',o.lotesDisponibles],['Stock disponible kg',o.stockDisponibleKg],['Pedidos confirmados',o.pedidosConfirmados],['Inspecciones aprobadas',o.inspeccionesAprobadas]];
  const estados=(o.exportacionesPorEstado||[]).map(x=>`<tr><td>${badge(x.estado)}</td><td>${x.total}</td></tr>`).join('');
  content.innerHTML=`<div class="section-head"><div><h2>Dashboard operativo</h2><p class="muted">Indicadores calculados en tiempo real desde MySQL.</p></div></div><div class="grid cards">${items.map(([n,v])=>`<div class="card"><div class="muted">${n}</div><div class="metric">${v??0}</div></div>`).join('')}</div><div class="grid" style="grid-template-columns:1fr 1fr;margin-top:18px"><div class="panel"><h3>Exportaciones por estado</h3>${table(['Estado','Total'],estados?[estados]:[])}</div><div class="panel"><strong>Sesión activa</strong><p>${escapeHtml(session.nombre)} · ${escapeHtml(session.rol.replaceAll('_',' '))}</p><p class="muted">La información es de consulta operativa y no sustituye validaciones oficiales.</p></div></div>`;
}

async function clientes(){
  const data=await request('/api/clientes');
  const canWrite=session.rol==='GESTOR_COMERCIAL';
  content.innerHTML=`<div class="section-head"><div><h2>Gestión de clientes</h2><p class="muted">Compradores internacionales asociados al proceso comercial.</p></div></div>${canWrite?`<div class="panel"><h3>Nuevo cliente</h3><form id="cliente-form" class="form-grid"><label>Razón social<input name="razonSocial" required></label><label>País (ISO-2)<input name="pais" maxlength="2" required placeholder="ES"></label><label>Identificación fiscal<input name="identificacionFiscal" required></label><label>Correo<input name="email" type="email" required></label><label>Teléfono<input name="telefono" required></label><label>Contacto principal<input name="contactoPrincipal" required></label><label class="full">Dirección<input name="direccion" required></label><div class="full"><button class="btn primary">Guardar cliente</button></div></form></div>`:''}${table(['Razón social','País','Identificación','Correo','Contacto','Estado'],data.map(c=>`<tr><td>${escapeHtml(c.razonSocial)}</td><td>${escapeHtml(c.pais)}</td><td>${escapeHtml(c.identificacionFiscal)}</td><td>${escapeHtml(c.email)}</td><td>${escapeHtml(c.contactoPrincipal)}</td><td>${badge(c.activo?'ACTIVO':'INACTIVO')}</td></tr>`))}`;
  if(canWrite) document.getElementById('cliente-form').onsubmit=async e=>{e.preventDefault();const f=Object.fromEntries(new FormData(e.target));try{await request('/api/clientes',{method:'POST',body:JSON.stringify(f)});toast('Cliente registrado');clientes()}catch(err){toast(err.message,true)}};
}

async function inventario(){
  const [productos,lotes]=await Promise.all([request('/api/productos'),request('/api/lotes')]);
  content.innerHTML=`<div class="section-head"><div><h2>Productos y lotes</h2><p class="muted">Inventario de café y cacao en kilogramos.</p></div></div><div class="grid" style="grid-template-columns:1fr 1fr"><div class="panel"><h3>Nuevo producto</h3><form id="producto-form" class="form-grid"><label>Código<input name="codigo" required></label><label>Tipo<select name="tipo"><option>CAFE</option><option>CACAO</option></select></label><label class="full">Nombre<input name="nombre" required></label><label class="full">Descripción<input name="descripcion"></label><div class="full"><button class="btn primary">Guardar</button></div></form></div><div class="panel"><h3>Nuevo lote</h3><form id="lote-form" class="form-grid"><label>Código<input name="codigo" required></label><label>Producto<select name="productoId">${productos.map(p=>`<option value="${p.id}">${escapeHtml(p.nombre)}</option>`).join('')}</select></label><label class="full">Origen<input name="origen" required></label><label>Fecha recepción<input name="fechaRecepcion" type="date" required></label><label>Cantidad kg<input name="cantidadInicialKg" type="number" step="0.001" min="0.001" required></label><label class="full">Proveedor ref.<input name="proveedorReferencia"></label><div class="full"><button class="btn primary">Guardar lote</button></div></form></div></div><h3>Productos</h3>${table(['Código','Nombre','Tipo','Unidad'],productos.map(p=>`<tr><td>${p.codigo}</td><td>${escapeHtml(p.nombre)}</td><td>${badge(p.tipo)}</td><td>${p.unidadMedida}</td></tr>`))}<h3 style="margin-top:22px">Lotes</h3>${table(['Código','Producto','Origen','Inicial kg','Reservado kg','Disponible kg','Estado'],lotes.map(l=>`<tr><td>${l.codigo}</td><td>${escapeHtml(l.producto)}</td><td>${escapeHtml(l.origen)}</td><td>${l.cantidadInicialKg}</td><td>${l.cantidadReservadaKg}</td><td><strong>${l.cantidadDisponibleKg}</strong></td><td>${badge(l.estado)}</td></tr>`))}`;
  document.getElementById('producto-form').onsubmit=async e=>{e.preventDefault();const f=Object.fromEntries(new FormData(e.target));try{await request('/api/productos',{method:'POST',body:JSON.stringify(f)});toast('Producto creado');inventario()}catch(err){toast(err.message,true)}};
  document.getElementById('lote-form').onsubmit=async e=>{e.preventDefault();const f=Object.fromEntries(new FormData(e.target));f.productoId=Number(f.productoId);f.cantidadInicialKg=Number(f.cantidadInicialKg);try{await request('/api/lotes',{method:'POST',body:JSON.stringify(f)});toast('Lote creado');inventario()}catch(err){toast(err.message,true)}};
}

async function pedidos(){
  const [ps,clientes,lotes]=await Promise.all([request('/api/pedidos'),request('/api/clientes'),request('/api/lotes')]);
  content.innerHTML=`<div class="section-head"><div><h2>Pedidos de exportación</h2><p class="muted">Reserva lotes disponibles para clientes internacionales.</p></div></div><div class="panel"><h3>Nuevo pedido</h3><form id="pedido-form" class="form-grid"><label>Cliente<select name="clienteId">${clientes.filter(c=>c.activo).map(c=>`<option value="${c.id}">${escapeHtml(c.razonSocial)}</option>`).join('')}</select></label><label>País destino<input name="paisDestino" maxlength="2" required placeholder="ES"></label><label>Lote<select name="loteId">${lotes.filter(l=>l.estado==='ACTIVO').map(l=>`<option value="${l.id}">${l.codigo} · disp. ${l.cantidadDisponibleKg} kg</option>`).join('')}</select></label><label>Cantidad kg<input name="cantidadKg" type="number" step="0.001" min="0.001" required></label><label class="full">Observaciones<textarea name="observaciones"></textarea></label><div class="full"><button class="btn primary">Crear pedido</button></div></form></div>${table(['Código','Cliente','Destino','Lote','Cantidad kg','Estado','Fecha'],ps.map(p=>`<tr><td>${p.codigo}</td><td>${escapeHtml(p.cliente)}</td><td>${p.paisDestino}</td><td>${p.lote||'-'}</td><td>${p.cantidadKg??'-'}</td><td>${badge(p.estado)}</td><td>${new Date(p.fechaPedido).toLocaleString()}</td></tr>`))}`;
  document.getElementById('pedido-form').onsubmit=async e=>{e.preventDefault();const f=Object.fromEntries(new FormData(e.target));f.clienteId=Number(f.clienteId);f.loteId=Number(f.loteId);f.cantidadKg=Number(f.cantidadKg);try{await request('/api/pedidos',{method:'POST',body:JSON.stringify(f)});toast('Pedido creado y lote reservado');pedidos()}catch(err){toast(err.message,true)}};
}

async function exportaciones(){
  const [exps,ps]=await Promise.all([request('/api/exportaciones'),request('/api/pedidos')]);
  content.innerHTML=`<div class="section-head"><div><h2>Expedientes de exportación</h2><p class="muted">Control documental interno; no reemplaza DIAN/VUCE.</p></div></div><div class="panel"><h3>Crear expediente</h3><form id="exp-form" class="form-grid"><label>Pedido confirmado<select name="pedidoId">${ps.filter(p=>p.estado==='CONFIRMADO').map(p=>`<option value="${p.id}">${p.codigo} · ${escapeHtml(p.cliente)}</option>`).join('')}</select></label><label>Salida prevista<input name="fechaSalidaPrevista" type="date"></label><div class="full"><button class="btn primary">Crear exportación</button></div></form></div>${table(['Código','Pedido','Cliente','Destino','Estado','Responsable','Checklist'],exps.map(e=>`<tr><td>${e.codigo}</td><td>${e.pedido}</td><td>${escapeHtml(e.cliente)}</td><td>${e.paisDestino}</td><td>${badge(e.estado)}</td><td>${escapeHtml(e.responsable)}</td><td><button class="btn" onclick="verChecklist(${e.id})">Ver</button></td></tr>`))}<div id="checklist" class="panel" style="margin-top:18px;display:none"></div>`;
  document.getElementById('exp-form').onsubmit=async e=>{e.preventDefault();const f=Object.fromEntries(new FormData(e.target));f.pedidoId=Number(f.pedidoId);if(!f.fechaSalidaPrevista)f.fechaSalidaPrevista=null;try{await request('/api/exportaciones',{method:'POST',body:JSON.stringify(f)});toast('Expediente creado');exportaciones()}catch(err){toast(err.message,true)}};
}
window.verChecklist=async id=>{try{const rows=await request(`/api/exportaciones/${id}/checklist`);const box=document.getElementById('checklist');box.style.display='block';box.innerHTML='<h3>Checklist documental</h3>'+table(['Requisito','Obligatorio','Estado','Archivo / versión','Carga'],rows.map(r=>`<tr><td>${escapeHtml(r.nombre_requisito)}</td><td>${r.obligatorio?'Sí':'No'}</td><td>${badge(r.estado)}</td><td>${escapeHtml(r.nombre_original||'-')} ${r.numero_version?`(v${r.numero_version})`:''}</td><td>${r.exportacion_requisito_id?`<form class="upload-doc" data-requisito="${r.exportacion_requisito_id}" data-exportacion="${id}"><input type="file" name="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.csv" required><button class="btn">Cargar</button></form>`:'-'}</td></tr>`));box.querySelectorAll('.upload-doc').forEach(form=>form.onsubmit=async e=>{e.preventDefault();const data=new FormData(form);try{if(!csrf)csrf=await request('/api/csrf');const res=await fetch(`/api/exportaciones/${id}/requisitos/${form.dataset.requisito}/documentos`,{method:'POST',body:data,credentials:'same-origin',headers:{[csrf.headerName]:csrf.token}});const type=res.headers.get('content-type')||'';const body=type.includes('application/json')?await res.json():await res.text();if(res.status===401){location='/login.html';return}if(!res.ok)throw new Error(body?.message|| (res.status===403?'Acceso denegado: verifica que tu sesión siga activa y tenga permiso para cargar documentos.':'No se pudo cargar el documento.'));toast('Documento cargado');verChecklist(id)}catch(err){toast(err.message||'No se pudo cargar el documento.',true)}})}catch(e){toast(e.message,true)}};

async function seguimiento(){
  const exps=await request('/api/exportaciones');
  content.innerHTML=`<div class="section-head"><div><h2>Seguimiento de exportación</h2><p class="muted">Actualiza el estado y conserva el historial logístico.</p></div></div><div class="panel"><form id="estado-form" class="form-grid"><label>Exportación<select name="id">${exps.map(e=>`<option value="${e.id}">${e.codigo} · ${e.estado}</option>`).join('')}</select></label><label>Nuevo estado<select name="estado"><option>LISTA_DESPACHO</option><option>ADUANA</option><option>EN_TRANSITO</option><option>ENTREGADA</option><option>CANCELADA</option></select></label><label class="full">Observación<input name="observacion" required></label><div class="full"><button class="btn primary">Registrar cambio</button> <button class="btn" type="button" id="hist-btn">Ver historial</button></div></form></div>${table(['Código','Pedido','Destino','Estado','Salida prevista','Salida real','Entrega'],exps.map(e=>`<tr><td>${e.codigo}</td><td>${e.pedido}</td><td>${e.paisDestino}</td><td>${badge(e.estado)}</td><td>${e.fechaSalidaPrevista||'-'}</td><td>${e.fechaSalidaReal?new Date(e.fechaSalidaReal).toLocaleString():'-'}</td><td>${e.fechaEntrega?new Date(e.fechaEntrega).toLocaleString():'-'}</td></tr>`))}<div id="history" class="panel" style="margin-top:18px;display:none"></div>`;
  document.getElementById('estado-form').onsubmit=async e=>{e.preventDefault();const f=Object.fromEntries(new FormData(e.target));const id=f.id;delete f.id;try{await request(`/api/exportaciones/${id}/estado`,{method:'POST',body:JSON.stringify(f)});toast('Estado actualizado');seguimiento()}catch(err){toast(err.message,true)}};
  document.getElementById('hist-btn').onclick=async()=>{const id=new FormData(document.getElementById('estado-form')).get('id');try{const rows=await request(`/api/exportaciones/${id}/historial`);const box=document.getElementById('history');box.style.display='block';box.innerHTML='<h3>Historial</h3>'+table(['Fecha','Anterior','Nuevo','Usuario','Observación'],rows.map(h=>`<tr><td>${new Date(h.fechaEvento).toLocaleString()}</td><td>${h.estadoAnterior||'-'}</td><td>${badge(h.estadoNuevo)}</td><td>${escapeHtml(h.usuario)}</td><td>${escapeHtml(h.observacion)}</td></tr>`))}catch(e){toast(e.message,true)}};
}

async function calidad(){
  const [rows,lotes]=await Promise.all([request('/api/calidad'),request('/api/lotes')]);
  content.innerHTML=`<div class="section-head"><div><h2>Inspecciones de calidad</h2><p class="muted">Registro de controles asociados a lotes de café y cacao.</p></div></div><div class="panel"><form id="calidad-form" class="form-grid"><label>Lote<select name="loteId">${lotes.map(l=>`<option value="${l.id}">${l.codigo} · ${escapeHtml(l.producto)}</option>`).join('')}</select></label><label>Humedad %<input name="humedadPorcentaje" type="number" min="0" max="100" step="0.01"></label><label>Evaluación de taza<input name="evaluacionTaza"></label><label>Aroma<input name="aroma"></label><label>Resultado<select name="resultado"><option>PENDIENTE</option><option>APROBADO</option><option>RECHAZADO</option></select></label><label>Defectos<input name="defectos"></label><label class="full">Observaciones<textarea name="observaciones"></textarea></label><div class="full"><button class="btn primary">Registrar inspección</button></div></form></div>${table(['Lote','Inspector','Fecha','Humedad','Resultado','Observaciones'],rows.map(i=>`<tr><td>${i.lote}</td><td>${escapeHtml(i.inspector)}</td><td>${new Date(i.fechaInspeccion).toLocaleString()}</td><td>${i.humedadPorcentaje??'-'}</td><td>${badge(i.resultado)}</td><td>${escapeHtml(i.observaciones||'-')}</td></tr>`))}`;
  document.getElementById('calidad-form').onsubmit=async e=>{e.preventDefault();const f=Object.fromEntries(new FormData(e.target));f.loteId=Number(f.loteId);f.humedadPorcentaje=f.humedadPorcentaje?Number(f.humedadPorcentaje):null;try{await request('/api/calidad',{method:'POST',body:JSON.stringify(f)});toast('Inspección registrada');calidad()}catch(err){toast(err.message,true)}};
}

async function trazabilidad(){
  content.innerHTML=`<div class="section-head"><div><h2>Consulta de trazabilidad</h2><p class="muted">Reconstruye el historial del lote sin modificar sus registros.</p></div></div><div class="panel"><form id="trace-form" class="toolbar"><input name="codigo" required value="DEMO-LOTE-001" placeholder="Código del lote"><button class="btn primary">Consultar</button></form></div><div id="trace-result"></div>`;
  document.getElementById('trace-form').onsubmit=async e=>{e.preventDefault();const codigo=new FormData(e.target).get('codigo');try{const d=await request(`/api/trazabilidad/${encodeURIComponent(codigo)}`);document.getElementById('trace-result').innerHTML=`<div class="panel"><h3>${escapeHtml(d.codigoLote)}</h3>${table(['Fecha','Origen','Evento','Descripción'],d.eventos.map(x=>`<tr><td>${new Date(x.fecha_evento).toLocaleString()}</td><td>${escapeHtml(x.origen_evento)}</td><td>${badge(x.tipo_evento)}</td><td>${escapeHtml(x.descripcion)}</td></tr>`))}</div>`}catch(err){toast(err.message,true)}};
}

init().catch(e=>{console.error(e);location='/login.html'});
