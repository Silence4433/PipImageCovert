package com.gtalee.covert;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;

/** 帧差补丁生产器：裁剪、统一调色板、容量预检、自动分组和递归切块。 */
public final class FramePatchComposeBuilder {
  private static final int SAFE_PIM_GROUP_SIZE = 32;
  public static interface ProgressListener { void onProgress(String message,int current,int total); }
  private static void progress(ProgressListener listener,String message,int current,int total){if(listener!=null)listener.onProgress(message,current,total);}
  public static final class Result { public int frameCount, patchCount, pipGroupCount; public File base; public List<File> patches=new ArrayList<File>(); public List<BufferedImage> mappedFrames=new ArrayList<BufferedImage>(); }
  private static final class Piece { BufferedImage image; int x,y,frame; String name; Piece(BufferedImage i,int px,int py,int f){image=i;x=px;y=py;frame=f;} }
  private static final class Crop { BufferedImage image; int dx,dy; Crop(BufferedImage i,int x,int y){image=i;dx=x;dy=y;} }
  private FramePatchComposeBuilder() {}
  private static void cleanOutput(File output){File[] old=output.listFiles(new FilenameFilter(){public boolean accept(File d,String n){return n.equals("base.png")||n.equals("frame_patches.compose.properties")||(n.startsWith("patch_")&&n.endsWith(".png"));}});if(old!=null)for(int i=0;i<old.length;i++)old[i].delete();}
  private static boolean diff(int a,int b,int t){return Math.max(Math.max(Math.abs(((a>>16)&255)-((b>>16)&255)),Math.abs(((a>>8)&255)-((b>>8)&255))),Math.abs((a&255)-(b&255)))>t||((a>>>24)&255)!=((b>>>24)&255);}
  public static Result build(File input,File output,int threshold,int gap,int minPixels,int maxPatch)throws IOException{return build(input,output,threshold,gap,minPixels,maxPatch,256,128,false,null);}
  public static Result build(File input,File output,int threshold,int gap,int minPixels,int maxPatch,int paletteColors,int alphaThreshold,boolean dither)throws IOException{return build(input,output,threshold,gap,minPixels,maxPatch,paletteColors,alphaThreshold,dither,null);}
  public static Result build(File input,File output,int threshold,int gap,int minPixels,int maxPatch,int paletteColors,int alphaThreshold,boolean dither,ProgressListener listener)throws IOException{
    if(maxPatch<1||maxPatch>255)throw new IOException("maxPatch必须在1到255之间");
    File[] fs=input.listFiles(new FilenameFilter(){public boolean accept(File d,String n){String x=n.toLowerCase();return x.endsWith(".png")||x.endsWith(".jpg")||x.endsWith(".jpeg");}});
    if(fs==null||fs.length==0)throw new IOException("no input images"); Arrays.sort(fs); if(!output.exists()&&!output.mkdirs())throw new IOException("cannot create output"); cleanOutput(output);
    BufferedImage base=ImageIO.read(fs[0]); if(base==null)throw new IOException("无法读取首帧");
    BufferedImage[] frames=new BufferedImage[fs.length]; for(int i=0;i<fs.length;i++){progress(listener,"正在读取第 "+(i+1)+"/"+fs.length+" 帧",i+1,fs.length);frames[i]=ImageIO.read(fs[i]);if(frames[i]==null)throw new IOException("无法读取："+fs[i].getName());if(frames[i].getWidth()!=base.getWidth()||frames[i].getHeight()!=base.getHeight())throw new IOException("输入的图片尺寸不一致，请更换为相同尺寸的图片");}
    int[] palette=makePalette(frames,paletteColors,alphaThreshold); IndexColorModel model=model(palette); BufferedImage baseMapped=map(base,model,palette,alphaThreshold,dither); File baseFile=new File(output,"base.png");ImageIO.write(baseMapped,"png",baseFile);
    List<Piece> pieces=new ArrayList<Piece>(); List<List<String>> refs=new ArrayList<List<String>>();
    for(int fi=0;fi<frames.length;fi++){progress(listener,"正在分析第 "+(fi+1)+"/"+frames.length+" 帧差",fi+1,frames.length);BufferedImage mapped=map(frames[fi],model,palette,alphaThreshold,dither);BufferedImage compareBase=baseMapped;List<String> row=new ArrayList<String>();for(int y=0;y<base.getHeight();y+=maxPatch)for(int x=0;x<base.getWidth();x+=maxPatch){int w=Math.min(maxPatch,base.getWidth()-x),h=Math.min(maxPatch,base.getHeight()-y),n=0;for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++)if(diff(compareBase.getRGB(x+xx,y+yy),mapped.getRGB(x+xx,y+yy),threshold))n++;if(n<minPixels)continue;Crop c=cropDifference(compareBase,mapped,x,y,w,h,gap);if(c==null)continue;splitRecursive(c.image,x+c.dx,y+c.dy,fi,pieces);}
      refs.add(row);}
    Result r=new Result();r.frameCount=frames.length;r.base=baseFile;for(int mi=0;mi<frames.length;mi++)r.mappedFrames.add(map(frames[mi],model,palette,alphaThreshold,dither));
    for(int i=0;i<pieces.size();i++){progress(listener,"正在输出图块 "+(i+1)+"/"+pieces.size(),i+1,pieces.size());Piece q=pieces.get(i);q.name=String.format("patch_%04d.png",i);File f=new File(output,q.name);ImageIO.write(q.image,"png",f);r.patches.add(f);refs.get(q.frame).add(q.name+"@"+q.x+","+q.y);}
    Properties p=new Properties();p.setProperty("format","frame-patch-v1");p.setProperty("frames",String.valueOf(frames.length));p.setProperty("base","base.png");p.setProperty("palette.colors",String.valueOf(palette.length));Map<String,Integer> groupByName=new HashMap<String,Integer>();
    for(int i=0;i<refs.size();i++)p.setProperty("frame."+i,join(refs.get(i)));
    int groups=buildGroups(p,pieces,baseMapped,groupByName,listener);p.setProperty("pim.groups",String.valueOf(groups));r.pipGroupCount=groups;
    progress(listener,"正在生成配置索引",pieces.size(),pieces.size()); for(int i=0;i<pieces.size();i++){Piece q=pieces.get(i);p.setProperty("patch."+q.name+".group",String.valueOf(groupByName.get(q.name).intValue()));p.setProperty("patch."+q.name+".x",String.valueOf(q.x));p.setProperty("patch."+q.name+".y",String.valueOf(q.y));p.setProperty("patch."+q.name+".width",String.valueOf(q.image.getWidth()));p.setProperty("patch."+q.name+".height",String.valueOf(q.image.getHeight()));}
    progress(listener,"正在写入 frame_patches.compose.properties",1,1);FileOutputStream out=new FileOutputStream(new File(output,"frame_patches.compose.properties"));try{p.store(out,"Generated by PipImageCovert");}finally{out.close();}r.patchCount=pieces.size();return r;
  }
  /* Find the largest valid prefix using exponential probing and binary search. */
  private static int buildGroups(Properties p,List<Piece> pieces,BufferedImage base,Map<String,Integer> groupByName,ProgressListener listener)throws IOException{
    progress(listener,"PIM capacity check: 0/"+pieces.size(),0,pieces.size());
    int groups=0,start=0; List<BufferedImage> baseParts=baseTiles(base);
    while(start<pieces.size()){
      int remaining=pieces.size()-start,low=0,high=1;
      while(high<=remaining&&validateRange(groups==0?baseParts:new ArrayList<BufferedImage>(),pieces,start,high)){low=high;high<<=1;progress(listener,"PIM probe: "+(start+low)+"/"+pieces.size(),start+low,pieces.size());}
      if(low==0)throw new IOException("A single patch cannot fit in PIM group");
      if(high>remaining)high=remaining;
      while(low+1<high){int mid=low+(high-low)/2;if(validateRange(groups==0?baseParts:new ArrayList<BufferedImage>(),pieces,start,mid))low=mid;else high=mid;progress(listener,"PIM boundary: "+(start+mid)+"/"+pieces.size(),start+mid,pieces.size());}
      List<BufferedImage> images=new ArrayList<BufferedImage>();if(groups==0)images.addAll(baseParts);List<Piece> groupPieces=new ArrayList<Piece>();
      for(int i=0;i<low;i++){Piece piece=pieces.get(start+i);images.add(trimPip(piece.image));groupPieces.add(piece);}
      writeGroup(p,groups,images,groupPieces);for(Piece piece:groupPieces)groupByName.put(piece.name,Integer.valueOf(groups));
      groups++;start+=low;progress(listener,"PIM group "+groups+": "+start+"/"+pieces.size(),start,pieces.size());
    }
    return groups;
  }
  private static boolean validateRange(List<BufferedImage> base,List<Piece> pieces,int start,int count)throws IOException{
    List<BufferedImage> images=new ArrayList<BufferedImage>(base.size()+count);images.addAll(base);
    for(int i=0;i<count;i++)images.add(trimPip(pieces.get(start+i).image));
    return WorkshopPimValidator.validate(images).valid;
  }
  private static void splitRecursive(BufferedImage im,int x,int y,int frame,List<Piece> out)throws IOException{List<BufferedImage> one=new ArrayList<BufferedImage>();one.add(trimPip(im));if(im.getWidth()<=255&&im.getHeight()<=255&&WorkshopPimValidator.validate(one).valid){out.add(new Piece(im,x,y,frame));return;}if(im.getWidth()<=1&&im.getHeight()<=1)throw new IOException("PIM容量预检无法切分图块");if(im.getWidth()>=im.getHeight()){int a=im.getWidth()/2;splitRecursive(im.getSubimage(0,0,a,im.getHeight()),x,y,frame,out);splitRecursive(im.getSubimage(a,0,im.getWidth()-a,im.getHeight()),x+a,y,frame,out);}else{int a=im.getHeight()/2;splitRecursive(im.getSubimage(0,0,im.getWidth(),a),x,y,frame,out);splitRecursive(im.getSubimage(0,a,im.getWidth(),im.getHeight()-a),x,y+a,frame,out);}}
  private static Crop cropDifference(BufferedImage base,BufferedImage s,int ox,int oy,int w,int h,int gap){int lx=w,ly=h,hx=-1,hy=-1;for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(diff(base.getRGB(ox+x,oy+y),s.getRGB(ox+x,oy+y),0)){if(x<lx)lx=x;if(y<ly)ly=y;if(x>hx)hx=x;if(y>hy)hy=y;}if(hx<0)return null;lx=Math.max(0,lx-gap);ly=Math.max(0,ly-gap);hx=Math.min(w-1,hx+gap);hy=Math.min(h-1,hy+gap);BufferedImage r=new BufferedImage(hx-lx+1,hy-ly+1,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<r.getHeight();y++)for(int x=0;x<r.getWidth();x++){int gx=ox+lx+x,gy=oy+ly+y;r.setRGB(x,y,s.getRGB(gx,gy));}return new Crop(r,lx,ly);}
  /** 与 FramePatchCtsBuilder.add(base) 相同：先裁掉透明边，再按 255x255 分块。 */
  private static BufferedImage trimPip(BufferedImage source){
    int l=source.getWidth(),t=source.getHeight(),r=-1,b=-1;
    for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)if((source.getRGB(x,y)>>>24)!=0){l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x);b=Math.max(b,y);}
    if(r<0)return new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
    BufferedImage out=new BufferedImage(r-l+1,b-t+1,BufferedImage.TYPE_INT_ARGB);
    for(int y=t;y<=b;y++)for(int x=l;x<=r;x++)out.setRGB(x-l,y-t,source.getRGB(x,y));
    return out;
  }
  private static List<BufferedImage> baseTiles(BufferedImage source){
    int left=source.getWidth(),top=source.getHeight(),right=-1,bottom=-1;
    for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)if((source.getRGB(x,y)>>>24)!=0){if(x<left)left=x;if(x>right)right=x;if(y<top)top=y;if(y>bottom)bottom=y;}
    if(right<0)left=top=right=bottom=0;
    BufferedImage cropped=new BufferedImage(right-left+1,bottom-top+1,BufferedImage.TYPE_INT_ARGB);
    for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)cropped.setRGB(x-left,y-top,source.getRGB(x,y));
    List<BufferedImage> tiles=new ArrayList<BufferedImage>();
    for(int y=0;y<cropped.getHeight();y+=255)for(int x=0;x<cropped.getWidth();x+=255)tiles.add(cropped.getSubimage(x,y,Math.min(255,cropped.getWidth()-x),Math.min(255,cropped.getHeight()-y)));
    return tiles;
  }  private static String join(List<String> a){StringBuilder s=new StringBuilder();for(String v:a){if(s.length()>0)s.append(';');s.append(v);}return s.toString();}
  private static void writeGroup(Properties p,int n,List<BufferedImage> a,List<Piece> pieces)throws IOException{WorkshopPimValidator.Result c=WorkshopPimValidator.validate(a);if(!c.valid)throw new IOException("PIM容量预检失败，分组"+n+"："+c.reason);p.setProperty("pim.group."+n+".count",String.valueOf(pieces.size()));p.setProperty("pim.group."+n+".pdata",String.valueOf(c.pdataLength));p.setProperty("pim.group."+n+".maxIdata",String.valueOf(c.maxIdataLength));}
  private static int[] makePalette(BufferedImage[] fs,int wanted,int alpha){Map<Integer,Integer> m=new HashMap<Integer,Integer>();for(BufferedImage im:fs)for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){int c=im.getRGB(x,y);if((c>>>24)<alpha)continue;Integer n=m.get(Integer.valueOf(c&0xffffff));m.put(Integer.valueOf(c&0xffffff),Integer.valueOf(n==null?1:n.intValue()+1));}if(wanted<2)wanted=2;int[] a=new int[Math.min(wanted-1,m.size())];List<Map.Entry<Integer,Integer>> es=new ArrayList<Map.Entry<Integer,Integer>>(m.entrySet());java.util.Collections.sort(es,new java.util.Comparator<Map.Entry<Integer,Integer>>(){public int compare(Map.Entry<Integer,Integer>a,Map.Entry<Integer,Integer>b){return b.getValue().intValue()-a.getValue().intValue();}});for(int i=0;i<a.length;i++)a[i]=es.get(i).getKey().intValue();int[] r=new int[a.length+1];r[0]=0;for(int i=0;i<a.length;i++)r[i+1]=0xff000000|a[i];return r;}
  private static IndexColorModel model(int[] p){byte[] r=new byte[p.length],g=new byte[p.length],b=new byte[p.length],a=new byte[p.length];for(int i=0;i<p.length;i++){r[i]=(byte)(p[i]>>16);g[i]=(byte)(p[i]>>8);b[i]=(byte)p[i];a[i]=(byte)(p[i]>>>24);}return new IndexColorModel(8,p.length,r,g,b,a);}
  private static BufferedImage map(BufferedImage s,IndexColorModel m,int[] p,int alpha,boolean d){WritableRaster r=m.createCompatibleWritableRaster(s.getWidth(),s.getHeight());BufferedImage o=new BufferedImage(m,r,false,null);for(int y=0;y<s.getHeight();y++)for(int x=0;x<s.getWidth();x++){int c=s.getRGB(x,y);if((c>>>24)<alpha){r.setSample(x,y,0,0);continue;}int best=1;long bd=Long.MAX_VALUE;for(int i=1;i<p.length;i++){int dr=((c>>16)&255)-((p[i]>>16)&255),dg=((c>>8)&255)-((p[i]>>8)&255),db=(c&255)-(p[i]&255);long z=dr*dr+dg*dg+db*db;if(z<bd){bd=z;best=i;}}r.setSample(x,y,0,best);}return o;}
}
