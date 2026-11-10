/* 설치형 앱에서만: AR 실측 탭을 기기 기능으로 바꾸고, 산출 내역을 기기에 쌓는 버튼을 붙인다 */
(function(){
  var A=window.AndroidApp, Q=window.__qto; if(!A||!Q) return;
  function $(id){ return document.getElementById(id); }
  function el(tag,attrs,text){ var e=document.createElement(tag); for (var k in attrs) e.setAttribute(k,attrs[k]); if(text!=null) e.textContent=text; return e; }

  /* ---- AR 실측 탭 ---- */
  var sm=$("v-ar")&&$("v-ar").querySelector("small"); if(sm) sm.textContent="이 기기";
  var card=document.querySelector("#view-ar .card");
  if(card){
    var badge=card.querySelector(".badge-st");
    function arState(){ var st={}; try{ st=JSON.parse(A.arStatus()); }catch(_){}
      badge.textContent=st.text||""; badge.classList.toggle("ok",!!st.ok); if(st.wait) setTimeout(arState,400); }
    arState();
    var ps=card.querySelectorAll(":scope > p");
    if(ps[0]) ps[0].textContent="휴대폰 카메라로 주변을 인식한 뒤 시작점과 끝점을 찍으면 실제 거리가 바로 나옵니다. 기준 치수를 넣지 않아도 됩니다. ARCore 깊이(Depth) 기능을 씁니다.";
    if(ps[1]) ps[1].textContent="기준 치수(기준 길이 또는 바닥 4점)를 아는 사진이면 ‘사진 물량’에서도 거리·면적·토공량을 잴 수 있습니다. 저장한 측정은 ‘저장된 산출 내역’에 모이고 CSV로 내보냅니다.";
    var h2=card.querySelector("h2"); if(h2) h2.textContent="AR 실측: 기준 치수 없이 2점 거리";
    var li=card.querySelectorAll("ol.steps li");
    if(li[1]) li[1].lastChild.textContent="화면 가운데 조준점을 시작점·끝점에 맞추고 ‘점 찍기’를 누릅니다. 조준점 아래에 깊이·평면·특징점 중 무엇으로 잡았는지 표시됩니다.";
    if(li[2]) li[2].lastChild.textContent="거리와 측정선이 들어간 사진, 측정점 좌표를 ‘저장된 산출 내역’에 남기고 CSV로 내보냅니다.";
    var dd=card.querySelectorAll("dl.kv dt");
    Array.prototype.forEach.call(dd,function(dt){ if(dt.textContent==="1차 개발 범위"){ dt.textContent="저장"; dt.nextElementSibling.textContent="측정 사진(측정선 포함) · 두 점 좌표 · 카메라 거리 · 깊이 사용 여부"; } });
    var row=card.querySelector(".row");
    if(row){ var link=row.querySelector("a"); if(link) link.remove();
      var go=el("button",{id:"arStart","class":"primary"},"AR 실측 시작"); go.addEventListener("click",function(){ A.openAR(); });
      var rec=el("button",{id:"recOpen"},"저장된 산출 내역"); rec.addEventListener("click",function(){ A.openRecords(); });
      row.insertBefore(rec,row.firstChild); row.insertBefore(go,row.firstChild);
      var gp=$("goPhotoLen"); if(gp) gp.classList.remove("primary"); }
    var ai=el("div",{"class":"row app-ai"});
    var aiTx=el("p",{}, A.aiReady()?"AI 자동 세기: 켜짐 (Claude 비전)":"AI 자동 세기: 꺼짐 · 개수 ‘범위 자동’은 기기 안에서 직접 셉니다");
    var aiBt=el("button",{id:"aiSet"},"AI 설정"); aiBt.addEventListener("click",function(){ A.openSettings(!!(Q.S.photo&&Q.S.items.length)); });
    ai.appendChild(aiTx); ai.appendChild(aiBt); card.appendChild(ai);
  }

  /* ---- 산출 내역 → 기기 저장 ---- */
  var csv=$("csvBtn");
  if(csv){
    var sv=el("button",{id:"recSave","class":"primary"},"산출 내역에 저장"); csv.parentNode.insertBefore(sv,csv);
    sv.addEventListener("click",function(){
      var r=Q.rows(), h=r[0], out=[];
      var pick=[[8,"m³"],[5,"m³"],[3,"m²"],[6,"m"],[7,"개"]];
      r.slice(1).forEach(function(row){ if(/합계|공제/.test(row[0])) return;
        for (var j=0;j<pick.length;j++){ var c=pick[j][0], v=row[c]; if(v!==""&&v!=null&&isFinite(+v)){
          out.push({ type:row[0], label:row[2]||(row[0]+" "+row[1]), value:+v, unit:pick[j][1],
            detail:"사진 물량 · "+h.map(function(k,i){ return i>2&&row[i]!==""?k+" "+row[i]:null; }).filter(Boolean).join(" / ") }); break; } } });
      if(!out.length){ $("repStatus").textContent="저장할 산출 항목이 없습니다."; return; }
      var b64=""; if(Q.S.photo){ var ed=Q.S.editing; Q.S.editing=false; Q.render(); try{ b64=Q.canvas().toDataURL("image/jpeg",0.88).split(",")[1]; }catch(_){} Q.S.editing=ed; Q.render(); }
      var n=A.saveRecords(JSON.stringify(out),b64);
      $("repStatus").textContent=n>0?n+"건을 산출 내역에 저장했습니다. ‘AR 실측 → 저장된 산출 내역’에서 볼 수 있습니다.":"저장하지 못했습니다.";
    });
  }

  /* ---- 뒤로 가기: 화면 → 그리기 → 수정 순으로 닫고, 남은 게 없으면 앱이 종료를 묻는다 ---- */
  window.__appBack=function(){
    if(Q.view()!=="photo"){ Q.setView("photo"); return true; }
    if(Q.inProgress()){ Q.cancelDraw(); return true; }
    if(Q.S.editing){ Q.setEditing(false); return true; }
    return false;
  };
})();
