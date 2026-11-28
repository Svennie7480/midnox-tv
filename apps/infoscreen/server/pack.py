import re,sys
src=open('src/server.php').read()
def nd(name,path):
    t=open(path).read()
    assert ('MXI_'+name+'_END') not in t
    return "const MXI_%s = <<<'MXI_%s_END'\n%s\nMXI_%s_END;\n"%(name,name,t,name)
consts=nd('EDITOR','src/editor.html')+nd('PLAYER','src/player.html')+nd('QR','src/qrcode.js')
out=src.replace("define('MXI_VER'",consts+"define('MXI_VER'",1)
open('dist/index.php','w').write(out)
print(len(out))
