/* 설치형 앱에서만: 웹 시제품이 쓰는 window.claude 기능(downloads, sample)을 기기 기능으로 잇는다 */
(function(){
  var A=window.AndroidApp; if(!A) return;
  document.documentElement.classList.add("in-app");
  function err(code){ var e=new Error(code); e.code=code; return e; }
  function toB64(data){ return new Promise(function(res,rej){
    if(typeof data==="string"){ try{ res(btoa(unescape(encodeURIComponent(data)))); }catch(e){ rej(e); } return; }
    var fr=new FileReader(); fr.onload=function(){ res(String(fr.result).split(",")[1]||""); }; fr.onerror=function(){ rej(fr.error); }; fr.readAsDataURL(data); }); }
  var downloads={ save:function(o){ return toB64(o.data).then(function(b){
    var mime=typeof o.data==="string"?"text/csv":(o.data.type||"application/octet-stream");
    var r=A.saveFile(String(o.filename||"file"),b,mime); if(r!=="ok") throw err(r); }); } };
  var pend={}, seq=0;
  window.__appAI={ done:function(id,ok,text){ var p=pend[id]; if(!p) return; delete pend[id];
    if(!ok){ p.rej(err(text||"failed")); return; } try{ p.res(JSON.parse(text)); }catch(e){ p.rej(err("bad_json")); } } };
  var sample={ limits:function(){ return Promise.resolve({ images:{ maxCount:1 } }); },
    json:function(prompt,opts){ var img=opts&&opts.images&&opts.images[0];
      return (img?toB64(img):Promise.resolve("")).then(function(b){ return new Promise(function(res,rej){
        var id=++seq; pend[id]={res:res,rej:rej};
        if(opts&&opts.signal) opts.signal.addEventListener("abort",function(){ if(pend[id]){ delete pend[id]; rej(err("aborted")); } });
        A.aiJson(id,String(prompt),b,img?(img.type||"image/jpeg"):""); }); }); } };
  window.claude={ use:function(n){ return Promise.resolve(n==="downloads"?downloads:(n==="sample"&&A.aiReady())?sample:null); } };
  try{ Object.defineProperty(navigator,"clipboard",{ configurable:true, value:{ writeText:function(t){ return A.copyText(String(t))?Promise.resolve():Promise.reject(err("denied")); } } }); }catch(_){}
})();
