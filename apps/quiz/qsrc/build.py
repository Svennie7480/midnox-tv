import json, hashlib, glob, os, sys
qs=[]; seen=set()
for f in sorted(glob.glob(os.path.join(os.path.dirname(__file__),'*.txt'))):
    for ln in open(f, encoding='utf-8'):
        ln=ln.strip()
        if not ln or ln.startswith('#'): continue
        p=ln.split('|'); assert len(p)==7, ln
        c,l,q=p[0],int(p[1]),p[2]; a=p[3:]
        assert len(set(a))==4, ln
        i=hashlib.md5(q.encode()).hexdigest()[:8]
        assert i not in seen, ln; seen.add(i)
        qs.append({"id":i,"c":c,"l":l,"q":q,"a":a})
cats={"allg":"Allgemeinwissen","geo":"Geografie","gesch":"Geschichte","sport":"Sport","musik":"Musik","film":"Film & TV","natur":"Natur & Wissenschaft","technik":"Technik","essen":"Essen & Trinken","nord":"Norddeutschland","sprache":"Sprache"}
for q in qs: assert q["c"] in cats, q
out={"v":int(sys.argv[1]) if len(sys.argv)>1 else 1,"cats":cats,"q":qs}
json.dump(out,open(os.path.join(os.path.dirname(__file__),'..','assets','questions.json'),'w',encoding='utf-8'),ensure_ascii=False,separators=(',',':'))
print(len(qs),"Fragen")
