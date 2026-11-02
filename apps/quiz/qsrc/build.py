import json, hashlib, glob, os, sys
qs=[]; seen=set()
for f in sorted(glob.glob(os.path.join(os.path.dirname(__file__),'*.txt'))):
    for ln in open(f, encoding='utf-8'):
        ln=ln.strip()
        if not ln or ln.startswith('#'): continue
        p=ln.split('|')
        if p[0] in ('est','ord'):
            t=p[0]; c,l,q=p[1],int(p[2]),p[3]
            i=hashlib.md5((t+q+'|'.join(p[4:])).encode()).hexdigest()[:8]
            assert i not in seen, ln; seen.add(i)
            if t=='est':
                assert len(p)==6, ln; qs.append({"id":i,"t":"est","c":c,"l":l,"q":q,"n":float(p[4]),"u":p[5]})
            else:
                assert len(p)==8 and len(set(p[4:]))==4, ln; qs.append({"id":i,"t":"ord","c":c,"l":l,"q":q,"o":p[4:]})
            continue
        assert len(p)==7, ln
        c,l,q=p[0],int(p[1]),p[2]; a=p[3:]
        assert len(set(a))==4, ln
        i=hashlib.md5(q.encode()).hexdigest()[:8]
        assert i not in seen, ln; seen.add(i)
        qs.append({"id":i,"c":c,"l":l,"q":q,"a":a})
cats={"allg":"Allgemeinwissen","geo":"Geografie","gesch":"Geschichte","sport":"Sport","musik":"Musik","film":"Film & TV","natur":"Natur & Wissenschaft","technik":"Technik","essen":"Essen & Trinken","nord":"Norddeutschland","de":"Deutschland & Regionen","sprache":"Sprache"}
for q in qs: assert q["c"] in cats, q
out={"v":int(sys.argv[1]) if len(sys.argv)>1 else 1,"cats":cats,"q":qs}
json.dump(out,open(os.path.join(os.path.dirname(__file__),'..','assets','questions.json'),'w',encoding='utf-8'),ensure_ascii=False,separators=(',',':'))
print(len(qs),"Fragen")
