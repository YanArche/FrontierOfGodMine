const el=id=>document.getElementById(id);
const browser=new URLSearchParams(location.hash.slice(1)).get("device");
history.replaceState(null,"","/");
async function post(path,body){
 const r=await fetch(path,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
 if(!r.ok)throw Error("Запрос отклонён ("+r.status+"). Проверьте приглашение, ник и срок действия кода.");
 return r.json();
}
async function auth(mode){
 const json=await post("/begin",{mode,name:el("name").value,invite:el("invite").value});
 const register=mode==="register";
 const options=register?PublicKeyCredential.parseCreationOptionsFromJSON(json):PublicKeyCredential.parseRequestOptionsFromJSON(json);
 const cred=await navigator.credentials[register?"create":"get"]({publicKey:options});
 const result=await post("/finish",cred.toJSON());el("status").textContent="Вы вошли как "+result.name;
 el("confirm").hidden=!browser;
}
for(const mode of ["login","register"])el(mode).onclick=()=>auth(mode).catch(e=>el("status").textContent=e.message);
el("approve").onclick=async()=>{try{await post("/approve",{browser,code:el("code").value});el("status").textContent="Вход подтверждён. Вернитесь в WoidWatch.";el("confirm").hidden=true;}catch(e){el("status").textContent=e.message;}};
