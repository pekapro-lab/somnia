# Genera assets/cover.png (1200x630) para la ficha del Estudio.
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, random
W,H=1200,630
img=Image.new('RGB',(W,H)); d=ImageDraw.Draw(img)
for y in range(H):
    t=y/H; d.line([(0,y),(W,y)],fill=(int(8+22*t),int(11+16*t),int(26+50*t)))
random.seed(3)
for _ in range(140):
    x,y=random.randint(0,W),random.randint(0,int(H*0.6)); r=random.choice([1,1,1,2])
    d.ellipse([x-r,y-r,x+r,y+r],fill=(248,250,252))
cx,cy,R=1035,175,105
glow=Image.new('RGBA',(W,H),(0,0,0,0)); g=ImageDraw.Draw(glow)
g.ellipse([cx-R*1.9,cy-R*1.9,cx+R*1.9,cy+R*1.9],fill=(244,196,100,60))
glow=glow.filter(ImageFilter.GaussianBlur(60)); img.paste(glow,(0,0),glow)
d=ImageDraw.Draw(img)
moon=Image.new('L',(W,H),0); m=ImageDraw.Draw(moon)
m.ellipse([cx-R,cy-R,cx+R,cy+R],fill=255); m.ellipse([cx-R+60,cy-R-30,cx+R+60,cy+R-30],fill=0)
img.paste((244,196,100),(0,0),moon)
for i,(col,amp,base) in enumerate([((49,46,129),18,470),((30,27,75),22,520),((167,139,250),10,440)]):
    pts=[(x,base+amp*math.sin(x/70+i*1.7)) for x in range(0,W+10,8)]
    if i<2: d.polygon(pts+[(W,H),(0,H)],fill=col)
    else: d.line(pts,fill=col,width=4)
d.line([(x,500+12*math.sin(x/55+0.5)) for x in range(0,W+10,8)],fill=(244,196,100),width=3)
fb='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'; fr='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
d.text((80,200),"Somnia Binaural",font=ImageFont.truetype(fb,80),fill=(248,250,252))
d.text((84,305),"Tonos binaurales y sonidos de la naturaleza",font=ImageFont.truetype(fr,32),fill=(244,196,100))
d.text((84,580),"Hecho con Grok",font=ImageFont.truetype(fr,24),fill=(203,213,225))
img.save('assets/cover.png')
