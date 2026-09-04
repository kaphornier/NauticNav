package de.nauticnav;

import android.content.Context;
import android.graphics.*;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

public class NavView extends View {
 private final Paint p=new Paint(3); private boolean night=false, showPos=true; private int sats=0; private float brightness=0.65f;
 private double distanceNm=0, sog=0, cog=0; private long startMs=0; private Location last; private final ArrayDeque<Point> history=new ArrayDeque<>();
 private static class Point{double sog,cog,seg; Point(double c,double d,double e){sog=c;cog=d;seg=e;}}
 private final SimpleDateFormat timeFmt=new SimpleDateFormat("HH:mm:ss",Locale.GERMANY);
 public NavView(Context c){super(c); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); setFocusable(true);}
 public void setSatellites(int n){sats=n;invalidate();}
 public void updateLocation(Location l){ if(startMs==0) startMs=System.currentTimeMillis(); double seg=0; if(last!=null) seg=last.distanceTo(l)/1852.0; distanceNm+=seg; sog=Math.max(0,l.hasSpeed()?l.getSpeed()*1.943844:0); cog=l.hasBearing()?l.getBearing():cog; history.addLast(new Point(sog,cog,seg)); while(totalHistory()>0.5 && history.size()>1) history.removeFirst(); last=new Location(l); invalidate(); }
 private double totalHistory(){double s=0; for(Point x:history)s+=x.seg; return s;}
 private double avgSog(){if(history.isEmpty())return 0; double v=0; for(Point x:history)v+=x.sog; return v/history.size();}
 private double avgCog(){if(history.isEmpty())return 0; double x=0,y=0; for(Point a:history){double r=Math.toRadians(a.cog);x+=Math.sin(r);y+=Math.cos(r);} double d=Math.toDegrees(Math.atan2(x,y)); return (d+360)%360;}
 private String pos(){return last==null?"—":String.format(Locale.US,"%.5f°, %.5f°",last.getLatitude(),last.getLongitude());}
 private String trip(){long sec=startMs==0?0:(System.currentTimeMillis()-startMs)/1000; return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
 private void text(Canvas c,String s,float x,float y,float size,int align){p.setTextSize(size);p.setTextAlign(align==0?Paint.Align.LEFT:align==1?Paint.Align.CENTER:Paint.Align.RIGHT);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));c.drawText(s,x,y,p);}
 private void box(Canvas c,float l,float t,float r,float b){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawRoundRect(l,t,r,b,18,18,p);p.setStyle(Paint.Style.FILL);}
 @Override protected void onDraw(Canvas c){super.onDraw(c); float w=getWidth(),h=getHeight(); int bg=night?Color.rgb(5,9,14):Color.WHITE, fg=night?Color.rgb(242,231,215):Color.rgb(20,35,48), blue=night?Color.rgb(225,142,72):Color.rgb(24,125,225); c.drawColor(bg);p.setColor(fg);
   text(c,"DISTANZ",w/2,54,15,1); p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(58);p.setTextAlign(Paint.Align.CENTER);c.drawText(String.format(Locale.US,"%.2f sm",distanceNm),w/2,112,p);
   float top=140, gap=10, bw=(w-36-gap)/2; p.setColor(fg); box(c,18,top,18+bw,top+92); box(c,28+bw,top,28+2*bw,top+92); p.setColor(blue); text(c,"SOG AKTUELL",18+bw/2,top+29,13,1);text(c,String.format(Locale.US,"%.1f kn",sog),18+bw/2,top+66,28,1);p.setColor(fg);text(c,"Ø LETZTE 0,5 sm",18+bw/2,top+84,10,1); p.setColor(blue); text(c,"SOG Ø",28+bw+bw/2,top+29,13,1);text(c,String.format(Locale.US,"%.1f kn",avgSog()),28+bw+bw/2,top+66,28,1);p.setColor(fg);text(c,"letzte 0,5 sm",28+bw+bw/2,top+84,10,1);
   top+=104;p.setColor(fg);box(c,18,top,18+bw,top+92);box(c,28+bw,top,28+2*bw,top+92);p.setColor(blue);text(c,"COG AKTUELL",18+bw/2,top+29,13,1);text(c,String.format(Locale.US,"%03.0f°",cog),18+bw/2,top+66,28,1);p.setColor(fg);text(c,"COG Ø",28+bw+bw/2,top+29,13,1);text(c,String.format(Locale.US,"%03.0f°",avgCog()),28+bw+bw/2,top+66,28,1);
   top+=112;p.setColor(fg);text(c,timeFmt.format(new Date()),w/2,top,26,1);text(c,"FAHRTZEIT  "+trip(),w/2,top+31,16,1); if(showPos)text(c,pos(),w/2,top+58,13,1);
   top+=78;p.setColor(night?Color.rgb(220,150,90):Color.rgb(50,90,115));box(c,18,top,w-18,top+48);text(c,"GPS  "+sats+" SAT",w/2-100,top+30,13,1);text(c,last==null?"Genauigkeit —":String.format(Locale.US,"±%.0f m",last.getAccuracy()),w/2,top+30,13,1);text(c,last!=null&&last.hasAccuracy()?"3D FIX":"NO FIX",w/2+100,top+30,13,1);
   top+=68;p.setColor(fg);box(c,18,top,w/2-8,top+48);box(c,w/2+8,top,w-18,top+48);text(c,night?"TAG":"NACHT",w/4,top+30,14,1);text(c,"HELLIGKEIT  "+Math.round(brightness*100)+"%",w*0.75f,top+30,14,1);
   float rb=h-82;p.setColor(night?Color.rgb(180,105,52):Color.rgb(25,115,205));c.drawRoundRect(24,rb,w-24,h-24,24,24,p);p.setColor(Color.WHITE);p.setTypeface(Typeface.DEFAULT_BOLD);text(c,"RESET  •  DISTANZ & FAHRTZEIT",w/2,rb+37,18,1);
 }
 @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX(),y=e.getY(),h=getHeight(),w=getWidth(); if(y>h-100){distanceNm=0;startMs=System.currentTimeMillis();history.clear();last=null;sog=0;cog=0;invalidate();return true;} if(y>h-170&&y<h-95){if(x<w/2){night=!night;invalidate();}else{brightness-=0.15f;if(brightness<0.25f)brightness=1f;if(getContext() instanceof MainActivity)((MainActivity)getContext()).setBrightness(brightness);invalidate();}return true;} return true; }
}
