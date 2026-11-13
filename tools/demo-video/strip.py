import sys,subprocess
from PIL import Image,ImageDraw,ImageFont
f=sys.argv[1]; ts=sys.argv[2:]; ims=[]
fn=ImageFont.truetype('/System/Library/Fonts/AppleSDGothicNeo.ttc',30,index=6)
for t in ts:
    subprocess.run(['ffmpeg','-v','error','-y','-ss',t,'-i',f+'.mp4','-frames:v','1','-vf','crop=1700:1100:860:150,scale=300:-1','st.png'])
    im=Image.open('st.png').convert('RGB'); ImageDraw.Draw(im).text((6,4),t,fill='red',font=fn); ims.append(im)
W=sum(i.width for i in ims); out=Image.new('RGB',(W,ims[0].height)); x=0
for i in ims: out.paste(i,(x,0)); x+=i.width
out.save('strip_'+f+'.png')
