/* 아이패드 홈 화면 앱 전용: 웹 시제품이 쓰는 window.claude 기능(downloads)을 Safari 기능으로 잇고, 화면을 아이패드에 맞춘다 */
(function(){
  var d=document.documentElement;
  d.classList.add("ipad");
  var standalone=window.navigator.standalone===true||(window.matchMedia&&matchMedia("(display-mode: standalone)").matches);
  if(standalone) d.classList.add("standalone");

  /* 두 손가락 확대는 사진만 — Safari 페이지 확대는 막는다 */
  ["gesturestart","gesturechange","gestureend"].forEach(function(t){
    document.addEventListener(t,function(e){ e.preventDefault(); },{passive:false}); });

  /* 가로 화면(2단)에서는 사진을 화면 높이의 72%까지 크게 */
  var WIDE="(min-width:1000px) and (orientation:landscape)";
  window.__qtoPhotoH=function(){ return window.matchMedia&&matchMedia(WIDE).matches?0.72:0.5; };

  function err(code){ var e=new Error(code); e.code=code; return e; }
  /* 저장: 공유 시트(파일에 저장 · AirDrop · 메일) → 안 되면 내려받기 */
  var downloads={ save:function(o){ return new Promise(function(res,rej){
    var name=String(o.filename||"file");
    var blob=typeof o.data==="string"?new Blob([o.data],{type:"text/csv;charset=utf-8"}):o.data;
    function anchor(){
      try{ var u=URL.createObjectURL(blob), a=document.createElement("a");
        a.href=u; a.download=name; a.rel="noopener"; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function(){ URL.revokeObjectURL(u); },60000); res(); }
      catch(e){ rej(err("failed")); } }
    var file=null; try{ file=new File([blob],name,{type:blob.type||"application/octet-stream"}); }catch(_){}
    if(file&&navigator.canShare&&navigator.share){
      var ok=false; try{ ok=navigator.canShare({files:[file]}); }catch(_){}
      if(ok){ navigator.share({files:[file],title:name}).then(function(){ res(); },function(e){
        if(e&&e.name==="AbortError") rej(err("declined")); else anchor(); }); return; } }
    anchor(); }); } };
  window.claude={ use:function(n){ return Promise.resolve(n==="downloads"?downloads:null); } };

  /* 오프라인 실행 */
  if("serviceWorker" in navigator) window.addEventListener("load",function(){ navigator.serviceWorker.register("sw.js").catch(function(){}); });

  document.addEventListener("DOMContentLoaded",function(){
    var s=document.querySelector(".drop-s");
    if(s) s.textContent="‘카메라로 촬영’을 누르면 아이패드 카메라가 바로 열립니다. 사진 앱에 있는 사진은 ‘앨범에서 선택’으로 불러옵니다.";
    var v=document.querySelector(".brand .ver"); if(v) v.textContent=v.textContent+" · iPad";
  });
})();
