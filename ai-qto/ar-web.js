/* AR 실측 (아이패드 · 브라우저용): 카메라 영상 + 자세 센서로 바닥 거리 · 면적 · 높이를 잰다.
   원리: 화면 가운데 조준점 = 카메라 광축. 기기 자세(기울기)로 광축 방향을 구하고,
   카메라 높이 h 아래의 바닥 평면과 만나는 점을 계산한다. (Android 설치형 앱은 ARCore를 쓰므로 이 모듈을 쓰지 않는다)
   정확도는 카메라 높이와 기울기 정확도에 좌우된다 → '기준 길이로 보정'으로 높이를 맞춘다. */
(function(){
  if(window.AndroidApp) return;
  var Q=window.__qto; if(!Q) return;
  function $(id){ return document.getElementById(id); }
  function el(tag,attrs,html){ var e=document.createElement(tag); if(attrs) for (var k in attrs) e.setAttribute(k,attrs[k]); if(html!=null) e.innerHTML=html; return e; }
  var D2R=Math.PI/180, LSK="qto.ar.v1";
  var CFG={ h:1.40, fov:63 }; try{ var sv=JSON.parse(localStorage.getItem(LSK)||"{}"); if(sv.h>0.3&&sv.h<3) CFG.h=sv.h; if(sv.fov>30&&sv.fov<100) CFG.fov=sv.fov; if(sv.cal) CFG.cal=sv.cal; }catch(_){}
  function saveCfg(){ try{ localStorage.setItem(LSK,JSON.stringify(CFG)); }catch(_){} }
  var ITEMS=[]; window.__arItems=ITEMS;

  /* ---------- 수학: 자세 → 광축 → 바닥 ---------- */
  /* DeviceOrientation: R = Rz(α)·Rx(β)·Ry(γ) (기기 좌표 → 지구 좌표: x 동, y 북, z 위). 후면 카메라는 기기 −z 방향 */
  function rot(a,b,g){ a*=D2R; b*=D2R; g*=D2R; var ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b),cg=Math.cos(g),sg=Math.sin(g);
    return [ ca*cg-sa*sb*sg, -sa*cb, ca*sg+sa*sb*cg,
             sa*cg+ca*sb*sg,  ca*cb, sa*sg-ca*sb*cg,
             -cb*sg,          sb,    cb*cg ]; }
  function axis(R){ return [-R[2],-R[5],-R[8]]; }
  function norm(v){ var l=Math.hypot(v[0],v[1],v[2])||1; return [v[0]/l,v[1]/l,v[2]/l]; }
  function ground(d,h){ if(d[2]>-0.07) return null; var t=h/(-d[2]); return { x:d[0]*t, y:d[1]*t }; }
  function heightAt(d,base,h){ var D=Math.hypot(base.x,base.y), r=Math.hypot(d[0],d[1]); if(r<1e-3) return null; return h+d[2]*D/r; }
  function sigmaD(D,h){ return (h*h+D*D)/h*(0.3*D2R); } /* 기울기 0.3° 흔들림에 따른 바닥 거리 오차 */
  window.__arMath={ rot:rot, axis:axis, ground:ground, heightAt:heightAt };

  /* ---------- 탭 화면 ---------- */
  var sm=$("v-ar")&&$("v-ar").querySelector("small"); if(sm) sm.textContent="카메라 · 센서";
  var tab=$("v-ar"); if(tab) tab.hidden=false;
  var card=document.querySelector("#view-ar .card"); if(!card) return;
  card.innerHTML=
    '<span class="badge-st ok" id="arBadge">카메라 + 자세 센서 · 아이패드 · 휴대폰 브라우저</span>'+
    '<h2>AR 실측: 바닥 거리 · 면적 · 높이</h2>'+
    '<p>카메라를 든 높이를 알면, 화면 가운데 조준점을 바닥에 맞추는 것만으로 거리를 잽니다. 기기 기울기로 조준점이 닿는 바닥 위치를 계산합니다. 사진 속 기준 치수는 필요 없습니다.</p>'+
    '<ol class="steps"><li><b>카메라 높이</b>바닥에서 카메라까지 높이를 넣거나, 아는 길이(타일 · 줄자)를 한 번 재서 맞춥니다.</li>'+
    '<li><b>조준 · 점 찍기</b>서 있는 자리에서 움직이지 말고, 조준점을 바닥의 시작점 · 끝점에 맞춰 ‘점 찍기’.</li>'+
    '<li><b>저장</b>측정선이 들어간 화면 사진과 값이 산출 내역에 들어갑니다.</li></ol>'+
    '<dl class="kv"><dt>잘 맞는 거리</dt><dd>발밑에서 1~5 m 바닥 (멀수록 오차가 빠르게 커집니다)</dd>'+
    '<dt>정확도</dt><dd>높이를 보정하면 3 m 거리에서 보통 수 cm 수준 · 기기와 손떨림에 따라 다르므로 줄자와 비교해 확인</dd>'+
    '<dt>지켜야 할 것</dt><dd>재는 동안 제자리에 서서 같은 높이로 들기 · 바닥이 평평한 곳 · 높이는 바닥점부터 위 끝까지</dd></dl>'+
    '<div class="arcal" id="arCalRow"><span>카메라 높이</span><b id="arHval"></b><span id="arCalNote" class="muted"></span></div>'+
    '<div class="row"><button id="arStart" class="primary">AR 실측 시작</button><button id="goPhotoLen">사진으로 거리 재기</button></div>'+
    '<div id="arMsg" class="armsg" hidden></div>'+
    '<div id="arListWrap" hidden><h3>이번 AR 측정</h3><div id="arList"></div><div class="row"><button id="arToRep">산출 내역 보기</button></div></div>';
  $("goPhotoLen").addEventListener("click",function(){ Q.setView("photo"); Q.setFn("length"); });
  $("arToRep").addEventListener("click",function(){ Q.setView("report"); });
  function calText(){ $("arHval").textContent=CFG.h.toFixed(2)+" m"; $("arCalNote").textContent=CFG.cal?"· 기준 길이 "+CFG.cal.mm.toLocaleString("ko-KR")+" mm로 보정함":"· 입력값 (보정 전)"; }
  calText();

  var css=el("style",null,
    '.arcal{display:flex;gap:6px;align-items:baseline;flex-wrap:wrap;padding:10px 12px;border-radius:10px;background:var(--bg2,#f3f5f9);margin:6px 0}.arcal .muted{color:var(--muted,#667085);font-size:13px}'+
    '.armsg{padding:10px 12px;border-radius:10px;background:#fff4e5;color:#7a3e00;margin-top:8px}'+
    '#arList table{width:100%;border-collapse:collapse;font-size:14px}#arList td,#arList th{padding:6px 8px;border-top:1px solid var(--line,#e3e6eb);text-align:left;vertical-align:middle}#arList img{width:88px;height:56px;object-fit:cover;border-radius:6px;display:block}'+
    '.arx{position:fixed;inset:0;z-index:9999;background:#000;color:#fff;touch-action:none;-webkit-user-select:none;user-select:none;font-family:inherit}'+
    '.arx video,.arx canvas{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}'+
    '.arx .bar{position:absolute;left:0;right:0;display:flex;gap:8px;align-items:center;justify-content:space-between;padding:10px max(14px,env(safe-area-inset-left,0px)) 10px max(14px,env(safe-area-inset-right,0px));pointer-events:none}'+
    '.arx .bar>*{pointer-events:auto}.arx .top{top:0;padding-top:max(10px,env(safe-area-inset-top,0px));background:linear-gradient(rgba(0,0,0,.55),rgba(0,0,0,0))}'+
    '.arx .bot{bottom:0;padding-bottom:max(14px,env(safe-area-inset-bottom,0px));background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.6))}'+
    '.arx button{font:inherit;font-weight:700;border:0;border-radius:999px;padding:10px 16px;background:rgba(255,255,255,.18);color:#fff;-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px)}'+
    '.arx button[disabled]{opacity:.4}.arx .seg{display:flex;background:rgba(0,0,0,.35);border-radius:999px;padding:3px}.arx .seg button{background:transparent;padding:8px 14px}.arx .seg button[aria-pressed=true]{background:#fff;color:#0f172a}'+
    '.arx .shot{width:76px;height:76px;border-radius:50%;background:#fff;color:#0f172a;font-size:13px;padding:0;box-shadow:0 0 0 4px rgba(255,255,255,.35)}'+
    '.arx .read{position:absolute;left:50%;top:calc(50% + 46px);transform:translateX(-50%);text-align:center;pointer-events:none;text-shadow:0 1px 3px rgba(0,0,0,.8)}'+
    '.arx .read b{display:block;font-size:30px;letter-spacing:-.01em}.arx .read span{font-size:14px;opacity:.9}'+
    '.arx .tip{position:absolute;left:50%;top:calc(env(safe-area-inset-top,0px) + 64px);transform:translateX(-50%);max-width:min(560px,90vw);text-align:center;background:rgba(0,0,0,.55);padding:8px 14px;border-radius:12px;font-size:14px;pointer-events:none}'+
    '.arx .sheet{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:min(420px,90vw);background:#fff;color:#0f172a;border-radius:16px;padding:16px;display:flex;flex-direction:column;gap:10px;box-shadow:0 10px 40px rgba(0,0,0,.4)}'+
    '.arx .sheet button{background:#eef1f6;color:#0f172a}.arx .sheet button.pri{background:#2f62f5;color:#fff}.arx .sheet input{font:inherit;font-size:20px;padding:8px 10px;border:1px solid #cfd6e2;border-radius:10px;width:100%;box-sizing:border-box}'+
    '.arx .sheet .r{display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap}.arx .sheet p{margin:0;font-size:14px;color:#3b4556}');
  document.head.appendChild(css);

  /* ---------- 전체 화면 측정 ---------- */
  var X=null;
  var ST={ mode:"dist", pts:[], base:null, top:null, cal:false, R:null, hist:[], last:0 };
  function build(){
    X=el("div",{"class":"arx",id:"arx"});
    X.innerHTML='<video playsinline muted autoplay></video><canvas></canvas>'+
      '<div class="bar top"><button id="arClose">닫기</button><div class="seg" role="group" aria-label="측정 종류"><button data-m="dist" aria-pressed="true">거리</button><button data-m="area" aria-pressed="false">면적</button><button data-m="height" aria-pressed="false">높이</button></div><button id="arH">높이 '+CFG.h.toFixed(2)+' m</button></div>'+
      '<div class="tip" id="arTip"></div><div class="read" id="arRead"><b></b><span></span></div>'+
      '<div class="bar bot"><button id="arUndo">되돌리기</button><button class="shot" id="arShot">점 찍기</button><button id="arDone">저장</button></div>';
    document.body.appendChild(X);
    X.querySelector(".seg").addEventListener("click",function(e){ var b=e.target.closest("button"); if(!b) return; setMode(b.getAttribute("data-m")); });
    $("arClose").addEventListener("click",stop);
    $("arShot").addEventListener("click",shoot);
    $("arUndo").addEventListener("click",undo);
    $("arDone").addEventListener("click",done);
    $("arH").addEventListener("click",heightSheet);
  }
  function setMode(m){ ST.mode=m; ST.pts=[]; ST.base=null; ST.top=null; ST.cal=false;
    Array.prototype.forEach.call(X.querySelectorAll(".seg button"),function(b){ b.setAttribute("aria-pressed",String(b.getAttribute("data-m")===m)); }); ui(); }
  function tip(t){ var e=$("arTip"); e.textContent=t||""; e.hidden=!t; }

  function onOri(e){ if(e.beta==null) return; var R=rot(e.alpha||0,e.beta,e.gamma||0), t=performance.now();
    ST.R=R; ST.hist.push({ t:t, d:axis(R) }); while(ST.hist.length&&t-ST.hist[0].t>350) ST.hist.shift(); ST.last=t; }
  /* 최근 0.35초 광축 평균과 흔들림(도) */
  function aimDir(){ var h=ST.hist; if(!h.length) return null; var s=[0,0,0]; h.forEach(function(q){ s[0]+=q.d[0]; s[1]+=q.d[1]; s[2]+=q.d[2]; }); var m=norm(s), dev=0;
    h.forEach(function(q){ var c=q.d[0]*m[0]+q.d[1]*m[1]+q.d[2]*m[2]; dev=Math.max(dev,Math.acos(Math.min(1,c))/D2R); }); return { d:m, shake:dev }; }

  function scrAngle(){ var o=window.orientation; if(typeof o==="number") return o; var s=screen.orientation&&screen.orientation.angle; return s==null?0:(s>180?s-360:s); }
  /* 지구 좌표(카메라 기준, m) → 화면 좌표 */
  function project(P,W,H){ var R=ST.R; if(!R) return null;
    var vx=R[0]*P[0]+R[3]*P[1]+R[6]*P[2], vy=R[1]*P[0]+R[4]*P[1]+R[7]*P[2], vz=R[2]*P[0]+R[5]*P[1]+R[8]*P[2];
    if(vz>-0.05) return null; var xc=vx/(-vz), yc=vy/(-vz), th=scrAngle()*D2R, xs=xc*Math.cos(th)-yc*Math.sin(th), ys=xc*Math.sin(th)+yc*Math.cos(th);
    var v=X.querySelector("video"), vw=v.videoWidth||W, vh=v.videoHeight||H, s=Math.max(W/vw,H/vh), f=Math.max(vw,vh)/2/Math.tan(CFG.fov/2*D2R)*s;
    return { x:W/2+f*xs, y:H/2-f*ys }; }
  function gP(p){ return [p.x,p.y,-CFG.h]; }

  function fmtM(m){ return m==null||!isFinite(m)?"–":(Math.abs(m)<1?(m*100).toFixed(1)+" cm":m.toFixed(2)+" m"); }
  function polyArea(p){ var a=0; for (var i=0;i<p.length;i++){ var q=p[i],r=p[(i+1)%p.length]; a+=q.x*r.y-r.x*q.y; } return Math.abs(a)/2; }
  function pathLen(p,close){ var s=0; for (var i=1;i<p.length;i++) s+=Math.hypot(p[i].x-p[i-1].x,p[i].y-p[i-1].y); if(close&&p.length>2) s+=Math.hypot(p[0].x-p[p.length-1].x,p[0].y-p[p.length-1].y); return s; }

  function current(){ var a=aimDir(); if(!a) return null; var g=ground(a.d,CFG.h); return { a:a, g:g }; }
  function ui(){ if(!X) return; var c=current(), rd=$("arRead"), b=rd.querySelector("b"), sp=rd.querySelector("span"), m=ST.mode;
    var stale=!ST.last||performance.now()-ST.last>1500;
    if(stale){ b.textContent="–"; sp.textContent="자세 센서를 기다리는 중…"; tip("기기를 천천히 움직여 보세요. 센서 값이 들어오지 않으면 ‘닫기’ 후 다시 시작하세요."); return; }
    var shake=c.a.shake>0.8?" · 흔들림 큼":"";
    if(ST.cal){ var n=ST.pts.length; b.textContent=n&&c.g?fmtM(Math.hypot(c.g.x-ST.pts[0].x,c.g.y-ST.pts[0].y)):"보정"; sp.textContent=(n?"끝점":"시작점")+"에 맞추고 ‘점 찍기’"+shake;
      tip("기준 길이 보정: 길이를 아는 바닥 선(타일 줄눈 · 줄자)의 양 끝을 차례로 찍으세요."); }
    else if(m==="height"){
      if(!ST.base){ b.textContent=c.g?fmtM(Math.hypot(c.g.x,c.g.y)):"바닥을 겨누세요"; sp.textContent="① 대상의 바닥 끝(밑동)에 맞추고 ‘점 찍기’"+shake; tip("높이: 대상이 바닥에 닿은 곳을 먼저 찍고, 그다음 위 끝을 찍습니다."); }
      else { var hh=heightAt(c.a.d,ST.base,CFG.h); b.textContent=fmtM(hh); sp.textContent="② 같은 대상의 위 끝에 맞추고 ‘점 찍기’"+shake; tip(""); } }
    else { if(!c.g){ b.textContent="바닥을 겨누세요"; sp.textContent="조준점이 수평선보다 아래 바닥에 있어야 합니다"; }
      else { var D=Math.hypot(c.g.x,c.g.y), pts=ST.pts.concat([c.g]);
        if(m==="dist"){ b.textContent=ST.pts.length?fmtM(pathLen(pts)):fmtM(D); sp.textContent=(ST.pts.length?"합계 · 다음 점에 맞추고 ‘점 찍기’":"발밑에서 "+fmtM(D)+" · 시작점에 맞추고 ‘점 찍기’")+" · 오차 약 ±"+fmtM(sigmaD(D,CFG.h))+shake; }
        else { b.textContent=pts.length>=3?polyArea(pts).toFixed(2)+" m²":ST.pts.length+"점"; sp.textContent="바닥 구역 모서리를 차례로 찍으세요"+(pts.length>=3?" · 둘레 "+fmtM(pathLen(pts,true)):"")+shake; }
        tip(D>6?"멀리 있는 점은 오차가 큽니다. 가까이 가서 재세요.":""); } }
    $("arUndo").disabled=!ST.pts.length&&!ST.base; var can=ST.mode==="dist"?ST.pts.length>=2:ST.mode==="area"?ST.pts.length>=3:!!ST.top; $("arDone").disabled=ST.cal||!can;
    $("arH").textContent="높이 "+CFG.h.toFixed(2)+" m"; }

  function draw(){ if(!X) return; var cv=X.querySelector("canvas"), W=cv.clientWidth, H=cv.clientHeight, dpr=window.devicePixelRatio||1;
    if(cv.width!==Math.round(W*dpr)||cv.height!==Math.round(H*dpr)){ cv.width=Math.round(W*dpr); cv.height=Math.round(H*dpr); }
    var g=cv.getContext("2d"); g.setTransform(dpr,0,0,dpr,0,0); g.clearRect(0,0,W,H); overlay(g,W,H,true); }
  function overlay(g,W,H,live){ var c=current(), pts=ST.pts.slice(), col=ST.cal?"#ffb020":"#2f62f5";
    if(live&&c&&c.g&&ST.mode!=="height") pts=pts.concat([c.g]);
    function line(a,b,lab){ var p=project(gP(a),W,H), q=project(gP(b),W,H); if(!p||!q) return;
      g.lineCap="round"; g.strokeStyle="rgba(0,0,0,.45)"; g.lineWidth=7; g.beginPath(); g.moveTo(p.x,p.y); g.lineTo(q.x,q.y); g.stroke();
      g.strokeStyle="#fff"; g.lineWidth=3.5; g.stroke(); if(lab) pill((p.x+q.x)/2,(p.y+q.y)/2,lab); }
    function dot(a,P){ var p=P||project(gP(a),W,H); if(!p) return; g.beginPath(); g.arc(p.x,p.y,8,0,7); g.fillStyle="#fff"; g.fill(); g.beginPath(); g.arc(p.x,p.y,5.5,0,7); g.fillStyle=col; g.fill(); }
    function pill(x,y,t){ g.font="700 15px system-ui, sans-serif"; var w=g.measureText(t).width+16; g.fillStyle="rgba(15,23,42,.82)"; g.beginPath(); if(g.roundRect) g.roundRect(x-w/2,y-13,w,26,13); else g.rect(x-w/2,y-13,w,26); g.fill(); g.fillStyle="#fff"; g.textAlign="center"; g.textBaseline="middle"; g.fillText(t,x,y+1); }
    if(ST.mode==="area"&&!ST.cal&&pts.length>=3){ var ps=pts.map(function(q){ return project(gP(q),W,H); }); if(ps.every(Boolean)){ g.beginPath(); ps.forEach(function(p,i){ i?g.lineTo(p.x,p.y):g.moveTo(p.x,p.y); }); g.closePath(); g.fillStyle="rgba(47,98,245,.28)"; g.fill(); } }
    for (var i=1;i<pts.length;i++){ var sl=Math.hypot(pts[i].x-pts[i-1].x,pts[i].y-pts[i-1].y); line(pts[i-1],pts[i],sl>=0.05?fmtM(sl):""); }
    if(ST.mode==="area"&&pts.length>=3) line(pts[pts.length-1],pts[0],"");
    ST.pts.forEach(function(q){ dot(q); });
    if(ST.mode==="height"&&ST.base){ var tp=ST.top?ST.top.h:(c?heightAt(c.a.d,ST.base,CFG.h):null), b0=project(gP(ST.base),W,H);
      if(tp!=null){ var t0=project([ST.base.x,ST.base.y,tp-CFG.h],W,H); if(b0&&t0){ g.strokeStyle="rgba(0,0,0,.45)"; g.lineWidth=7; g.beginPath(); g.moveTo(b0.x,b0.y); g.lineTo(t0.x,t0.y); g.stroke(); g.strokeStyle="#fff"; g.lineWidth=3.5; g.stroke(); dot(null,t0); pill(t0.x,t0.y-24,fmtM(tp)); } }
      dot(ST.base); }
    if(live){ var cx=W/2, cy=H/2; g.lineWidth=2; g.strokeStyle="rgba(0,0,0,.5)"; g.beginPath(); g.arc(cx,cy,19,0,7); g.stroke(); g.strokeStyle="#fff"; g.beginPath(); g.arc(cx,cy,17,0,7); g.stroke();
      g.beginPath(); g.moveTo(cx-28,cy); g.lineTo(cx-9,cy); g.moveTo(cx+9,cy); g.lineTo(cx+28,cy); g.moveTo(cx,cy-28); g.lineTo(cx,cy-9); g.moveTo(cx,cy+9); g.lineTo(cx,cy+28); g.stroke();
      g.beginPath(); g.arc(cx,cy,2.5,0,7); g.fillStyle=c&&c.a.shake>0.8?"#ffb020":"#22c55e"; g.fill(); } }

  function loop(){ if(!X) return; draw(); ui(); requestAnimationFrame(loop); }

  function shoot(){ var c=current(); if(!c){ return; }
    if(c.a.shake>1.5){ tip("기기가 흔들리고 있습니다. 잠시 멈췄다가 다시 누르세요."); return; }
    if(ST.mode==="height"&&!ST.cal){
      if(!ST.base){ if(!c.g){ tip("밑동은 바닥이므로 조준점을 아래로 내려 바닥에 맞추세요."); return; } ST.base=c.g; }
      else { var hh=heightAt(c.a.d,ST.base,CFG.h); if(hh==null) return; ST.top={ h:hh }; }
      buzz(); return; }
    if(!c.g){ tip("조준점을 바닥에 맞추세요."); return; }
    ST.pts.push(c.g); buzz();
    if(ST.cal&&ST.pts.length===2) calSheet(); }
  function buzz(){ try{ navigator.vibrate&&navigator.vibrate(15); }catch(_){} }
  function undo(){ if(ST.mode==="height"&&!ST.cal){ if(ST.top) ST.top=null; else ST.base=null; return; } ST.pts.pop(); }

  function snapImg(){ var v=X.querySelector("video"), vw=v.videoWidth, vh=v.videoHeight; if(!vw) return "";
    var k=Math.min(1,1280/Math.max(vw,vh)), W=Math.round(vw*k), H=Math.round(vh*k), c=document.createElement("canvas"); c.width=W; c.height=H; var g=c.getContext("2d");
    try{ g.drawImage(v,0,0,W,H); }catch(_){ return ""; }
    /* 화면(cover)과 같은 시야로 그리도록 화면 크기 대신 영상 크기로 투영 */
    var sv=X.querySelector("canvas"); var cw=sv.clientWidth, ch=sv.clientHeight; g.save(); var s=Math.max(cw/vw,ch/vh)*k; g.translate(W/2,H/2); g.scale(k/s*1,k/s*1); g.translate(-cw/2,-ch/2); overlay(g,cw,ch,false); g.restore();
    try{ return c.toDataURL("image/jpeg",0.82); }catch(_){ return ""; } }
  function done(){ var it=null, m=ST.mode;
    if(m==="dist"&&ST.pts.length>=2){ var segs=[]; for (var i=1;i<ST.pts.length;i++) segs.push(Math.hypot(ST.pts[i].x-ST.pts[i-1].x,ST.pts[i].y-ST.pts[i-1].y));
      it={ kind:"거리", value:pathLen(ST.pts), unit:"m", note:segs.length>1?"구간 "+segs.map(function(s){ return s.toFixed(2); }).join(" + "):"" }; }
    else if(m==="area"&&ST.pts.length>=3) it={ kind:"면적", value:polyArea(ST.pts), unit:"m²", note:"둘레 "+pathLen(ST.pts,true).toFixed(2)+" m · "+ST.pts.length+"점" };
    else if(m==="height"&&ST.top) it={ kind:"높이", value:ST.top.h, unit:"m", note:"밑동까지 "+Math.hypot(ST.base.x,ST.base.y).toFixed(2)+" m" };
    if(!it) return;
    it.h=CFG.h; it.cal=!!CFG.cal; it.img=snapImg(); it.t=new Date(); it.no=ITEMS.filter(function(q){ return q.kind===it.kind; }).length+1; it.label="AR "+it.kind+" "+it.no;
    ITEMS.push(it); list(); setMode(m); tip(it.label+" 저장: "+(it.unit==="m²"?it.value.toFixed(2)+" m²":fmtM(it.value))); }

  /* ---------- 카메라 높이 · 보정 ---------- */
  function sheet(html){ var s=el("div",{"class":"sheet"},html); X.appendChild(s); return s; }
  function heightSheet(){ if(X.querySelector(".sheet")) return;
    var s=sheet('<b>카메라 높이 (바닥 → 카메라)</b><p>서서 가슴 앞에 들면 보통 1.2~1.5 m입니다. 정확히 하려면 길이를 아는 바닥 선을 한 번 재서 보정하세요.</p>'+
      '<input id="arHin" type="number" inputmode="decimal" step="1" min="30" max="300" value="'+Math.round(CFG.h*100)+'"><p>단위: cm</p>'+
      '<div class="r"><button id="arCalGo">기준 길이로 보정</button><button id="arHx">취소</button><button class="pri" id="arHok">적용</button></div>');
    s.querySelector("#arHx").onclick=function(){ s.remove(); };
    s.querySelector("#arHok").onclick=function(){ var v=+s.querySelector("#arHin").value; if(v>=30&&v<=300){ CFG.h=v/100; CFG.cal=null; saveCfg(); calText(); } s.remove(); };
    s.querySelector("#arCalGo").onclick=function(){ s.remove(); ST.cal=true; ST.pts=[]; ST.base=null; ST.top=null; }; }
  function calSheet(){ var meas=Math.hypot(ST.pts[1].x-ST.pts[0].x,ST.pts[1].y-ST.pts[0].y);
    var s=sheet('<b>실제 길이를 넣으세요</b><p>지금 높이 '+CFG.h.toFixed(2)+' m로 잰 값: '+(meas*1000).toFixed(0)+' mm</p><input id="arCin" type="number" inputmode="decimal" placeholder="예: 1000" min="100"><p>단위: mm</p>'+
      '<div class="r"><button id="arCx">취소</button><button class="pri" id="arCok">보정</button></div>');
    s.querySelector("#arCx").onclick=function(){ s.remove(); ST.cal=false; ST.pts=[]; };
    s.querySelector("#arCok").onclick=function(){ var mm=+s.querySelector("#arCin").value; if(!(mm>=50)||!(meas>0.01)) return;
      CFG.h=Math.min(3,Math.max(0.3,CFG.h*(mm/1000)/meas)); CFG.cal={ mm:mm, t:Date.now() }; saveCfg(); calText(); s.remove(); ST.cal=false; ST.pts=[];
      tip("보정했습니다: 카메라 높이 "+CFG.h.toFixed(2)+" m. 이 높이로 들고 재세요."); }; setTimeout(function(){ var i=s.querySelector("#arCin"); i&&i.focus(); },50); }

  /* ---------- 시작 · 종료 ---------- */
  var stream=null, wake=null;
  function msg(t){ var m=$("arMsg"); m.textContent=t||""; m.hidden=!t; }
  async function start(){ msg("");
    try{ if(typeof DeviceOrientationEvent!=="undefined"&&typeof DeviceOrientationEvent.requestPermission==="function"){ var r=await DeviceOrientationEvent.requestPermission(); if(r!=="granted"){ msg("동작 및 방향 접근을 허용해야 AR 실측을 쓸 수 있습니다. 설정 › Safari › 동작 및 방향 접근을 켜고 다시 시도하세요."); return; } } }
    catch(e){ msg("자세 센서 권한을 받지 못했습니다. 화면을 다시 누르거나 페이지를 새로 고친 뒤 시도하세요."); return; }
    if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){ msg("이 브라우저에서는 카메라를 쓸 수 없습니다. Safari로 열어 주세요."); return; }
    try{ stream=await navigator.mediaDevices.getUserMedia({ audio:false, video:{ facingMode:{ ideal:"environment" }, width:{ ideal:1920 }, height:{ ideal:1440 } } }); }
    catch(e){ msg(e&&e.name==="NotAllowedError"?"카메라 접근을 허용해야 합니다. 설정 › Safari › 카메라에서 허용으로 바꾸세요.":"카메라를 열지 못했습니다 ("+(e&&e.name||"오류")+")."); return; }
    build(); var v=X.querySelector("video"); v.srcObject=stream; try{ await v.play(); }catch(_){}
    ST.hist=[]; ST.last=0; ST.R=null; setMode("dist"); window.addEventListener("deviceorientation",onOri);
    try{ wake=navigator.wakeLock&&await navigator.wakeLock.request("screen"); }catch(_){}
    requestAnimationFrame(loop);
    setTimeout(function(){ if(X&&!ST.last){ stop(); msg("자세 센서 값이 들어오지 않습니다. 이 기기에는 기울기 센서가 없거나 막혀 있습니다."); } },2500); }
  function stop(){ window.removeEventListener("deviceorientation",onOri); if(stream){ stream.getTracks().forEach(function(t){ t.stop(); }); stream=null; }
    try{ wake&&wake.release(); }catch(_){} wake=null; if(X){ X.remove(); X=null; } }
  $("arStart").addEventListener("click",start);
  document.addEventListener("visibilitychange",function(){ if(document.hidden&&X) stop(); });

  /* ---------- 목록 · 산출 내역 ---------- */
  function list(){ var w=$("arListWrap"); w.hidden=!ITEMS.length; if(!ITEMS.length) return;
    $("arList").innerHTML='<table><thead><tr><th>측정</th><th>값</th><th>근거</th><th>화면</th><th></th></tr></thead><tbody>'+ITEMS.map(function(it,i){
      return '<tr><td><b>'+it.label+'</b></td><td>'+(it.unit==="m²"?it.value.toFixed(2)+" m²":it.value.toFixed(2)+" m")+'</td><td>'+(it.note||"")+' · 높이 '+it.h.toFixed(2)+' m'+(it.cal?" (보정)":"")+'</td><td>'+(it.img?'<img alt="" src="'+it.img+'">':"")+'</td><td><button data-del="'+i+'">삭제</button></td></tr>'; }).join("")+'</tbody></table>';
    Array.prototype.forEach.call($("arList").querySelectorAll("[data-del]"),function(b){ b.onclick=function(){ ITEMS.splice(+b.getAttribute("data-del"),1); list(); }; }); }
  window.__qtoExtraRows=function(){ return ITEMS.map(function(it){ var v=it.value;
    return it.kind==="면적"?["AR 면적",it.no,it.label+" ("+it.note+")",v.toFixed(2),"","","","","","",""]
      :["AR "+it.kind,it.no,it.label+(it.note?" ("+it.note+")":""),"","","",v.toFixed(2),"","","",""]; }); };
})();
