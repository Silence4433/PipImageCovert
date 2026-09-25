package com.gtalee.covert;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 与旧版SWTUtils.getBestLayout等价的纯JDK布局实现。 */
public final class PimLayout {
    public static final class Result { public Rectangle[] frames; public Rectangle[] bounds; }
    private PimLayout() {}
    public static Result layout(Rectangle[] frames) {
        Result out = new Result();
        if (frames.length == 0) { out.frames = frames; out.bounds = new Rectangle[] { new Rectangle(0,0,0,0) }; return out; }
        int total = 0; for (Rectangle r : frames) total += r.width * r.height;
        Rectangle all = bestImpl(frames, 0, frames.length);
        if (all.width * all.height <= total * 102 / 100) { out.frames=frames; out.bounds=new Rectangle[]{all}; return out; }
        int bestType=-1,bestSplit=-1,bestArea=all.width*all.height;
        for(int type=0;type<=4;type++) {
            Rectangle[] sorted=copy(frames); if(type!=0) sort(sorted,type);
            for(int split=1;split<sorted.length;split++) { Rectangle a=bestImpl(sorted,0,split), b=bestImpl(sorted,split,sorted.length-split); int area=a.width*a.height+b.width*b.height; if(area<bestArea){bestType=type;bestSplit=split;bestArea=area;} }
        }
        Rectangle[] bounds;
        if(bestType<0) bounds=new Rectangle[]{bestImpl(frames,0,frames.length)};
        else { Rectangle[] sorted=copy(frames); sort(sorted,bestType); Rectangle a=bestImpl(sorted,0,bestSplit), b=bestImpl(sorted,bestSplit,sorted.length-bestSplit); for(int i=bestSplit;i<sorted.length;i++)sorted[i].x|=1<<14; bounds=new Rectangle[]{a,b}; }
        out.frames=frames; out.bounds=bounds; return out;
    }
    private static Rectangle[] copy(Rectangle[] a){Rectangle[] b=new Rectangle[a.length];for(int i=0;i<a.length;i++)b[i]=new Rectangle(a[i]);return b;}
    private static void sort(Rectangle[] a, final int type){Arrays.sort(a,new Comparator<Rectangle>(){public int compare(Rectangle x,Rectangle y){if(type==1)return y.height-x.height;if(type==2)return y.width-x.width;if(type==3)return y.width*y.height-x.width*x.height;double a=(double)x.width/x.height,b=(double)y.width/y.height;return a<b?-1:a>b?1:0;}public boolean equals(Object o){return true;}});}
    private static Rectangle bestImpl(Rectangle[] f,int start,int count){int minw=10000000,maxw=0,totalw=0;for(int i=start;i<start+count;i++){minw=Math.min(minw,f[i].width);maxw=Math.max(maxw,f[i].width);totalw+=f[i].width;}int bestArea=Integer.MAX_VALUE,bestW=0,bestOff=Integer.MAX_VALUE;for(int w=maxw;w<=totalw;w+=minw){Rectangle r=bestAt(f,start,count,w);int area=r.width*r.height,off=Math.abs(r.width-r.height);if(area<bestArea||(area==bestArea&&off<bestOff)){bestArea=area;bestW=w;bestOff=off;}}return bestAt(f,start,count,bestW);}
    private static Rectangle bestAt(Rectangle[] frames,int start,int count,int fit){Rectangle[] a=new Rectangle[count];System.arraycopy(frames,start,a,0,count);Arrays.sort(a,new Comparator<Rectangle>(){public int compare(Rectangle x,Rectangle y){return y.height-x.height;}public boolean equals(Object o){return true;}});List<Rectangle> free=new ArrayList<Rectangle>();int right=0,bottom=0;for(int i=0;i<a.length;i++){Rectangle r=a[i];int bi=findBest(free,r);if(bi>=0){Rectangle t=free.get(bi);r.x=t.x;r.y=t.y;if(r.width==t.width&&r.height==t.height)free.remove(bi);else if(r.width==t.width){t.y+=r.height;t.height-=r.height;}else if(r.height==t.height){t.x+=r.width;t.width-=r.width;}else{Rectangle n=new Rectangle(t.x,t.y+r.height,r.width,t.height-r.height);t.x+=r.width;t.width-=r.width;merge(free,n);}}else if(right+r.width<=fit){r.x=right;r.y=0;right+=r.width;if(r.height<bottom)merge(free,new Rectangle(r.x,r.height,r.width,bottom-r.height));else if(r.height>bottom){extend(free,bottom,r.height-bottom,0,right-r.width);bottom=r.height;}}else{bi=findMinY(free,r,bottom);int add;if(bi>=0){add=r.height-free.get(bi).height;extend(free,bottom,add,0,right);}else{add=r.height;right=Math.max(right,r.width);extend(free,bottom,add,r.width,right);free.add(new Rectangle(0,bottom,r.width,r.height));}bottom+=add;i--;}}
        return new Rectangle(0,0,right,bottom);}
    private static int findBest(List<Rectangle> l,Rectangle t){int bi=-1,best=100000000;for(int i=0;i<l.size();i++){Rectangle r=l.get(i);int wo=r.width-t.width,ho=r.height-t.height;if(wo>=0&&ho>=0&&wo*2+ho<best){bi=i;best=wo*2+ho;}}return bi;}
    private static int findMinY(List<Rectangle> l,Rectangle t,int bottom){int bi=-1,y=100000000;for(int i=0;i<l.size();i++){Rectangle r=l.get(i);if(r.y+r.height<bottom)continue;if(r.width>=t.width&&r.y<y){bi=i;y=r.y;}}return bi;}
    private static void merge(List<Rectangle> l,Rectangle t){for(Rectangle s:l){if(s.x==t.x&&s.width==t.width){if(s.y+s.height==t.y){s.height+=t.height;return;}if(s.y==t.y+t.height){s.y=t.y;s.height+=t.height;return;}}else if(s.y==t.y&&s.height==t.height){if(s.x+s.width==t.x){s.width+=t.width;return;}if(s.x==t.x+t.width){s.x=t.x;s.width+=t.width;return;}}}l.add(t);}
    private static void extend(List<Rectangle> l,int curY,int addY,int startX,int right){Rectangle[] a=l.toArray(new Rectangle[l.size()]);Arrays.sort(a,new Comparator<Rectangle>(){public int compare(Rectangle x,Rectangle y){return x.x-y.x;}public boolean equals(Object o){return true;}});int cur=startX;for(Rectangle r:a){if(r.y+r.height<curY)continue;if(cur>r.x)continue;if(cur<r.x)l.add(new Rectangle(cur,curY,r.x-cur,addY));r.height+=addY;cur=r.x+r.width;}if(cur<right)l.add(new Rectangle(cur,curY,right-cur,addY));}
}