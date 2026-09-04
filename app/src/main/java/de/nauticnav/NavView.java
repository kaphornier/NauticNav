package de.nauticnav;

import android.content.Context;
import android.graphics.*;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

public class NavView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean night = false, showPos = true;
    private int sats = 0;
    private float brightness = 0.65f;
    private double distanceNm = 0, sog = 0, cog = 0;
    private long startMs = 0;
    private Location last;
    private final ArrayDeque<Point> history = new ArrayDeque<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY);

    private static class Point { double sog, cog, seg; Point(double s,double c,double g){sog=s;cog=c;seg=g;} }
    public NavView(Context c){ super(c); setFocusable(true); }
    public void setSatellites(int n){sats=n;invalidate();}
    public void updateLocation(Location l){
        if(startMs==0)startMs=System.currentTimeMillis();
        double seg=last==null?0:last.distanceTo(l)/1852.0; distanceNm+=seg;
        sog=Math.max(0,l.hasSpeed()?l.getSpeed()*1.943844:0); if(l.hasBearing())cog=l.getBearing();
        history.addLast(new Point(sog,cog,seg)); while(totalHistory()>0.5&&history.size()>1)history.removeFirst();
        last=new Location(l); invalidate();
    }
    private double totalHistory(){double s=0;for(Point x:history)s+=x.seg;return s;}
    private double avgSog(){if(history.isEmpty())return 0;double v=0;for(Point x:history)v+=x.sog;return v/history.size();}
    private double avgCog(){if(history.isEmpty())return 0;double x=0,y=0;for(Point a:history){double r=Math.toRadians(a.cog);x+=Math.sin(r);y+=Math.cos(r);}return(Math.toDegrees(Math.atan2(x,y))+360)%360;}
    private String pos(){return last==null?"—":String.format(Locale.US,"%.5f°, %.5f°",last.getLatitude(),last.getLongitude());}
    private String trip(){long s=startMs==0?0:(System.currentTimeMillis()-startMs)/1000;return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);}

    private double moonAgeDays(){double d=(System.currentTimeMillis()-947182440000L)/86400000.0,a=d%29.530588853;return a<0?a+29.530588853:a;}
    private String moonPhase(){double a=moonAgeDays();if(a<1.85||a>=27.68)return"Neumond";if(a<7.38)return"Zunehmende Sichel";if(a<9.23)return"Erstes Viertel";if(a<14.77)return"Zunehmender Mond";if(a<16.62)return"Vollmond";if(a<22.15)return"Abnehmender Mond";if(a<24.00)return"Letztes Viertel";return"Abnehmende Sichel";}
    private String tideAge(){double a=moonAgeDays();if(a<2.5||a>=27.0||(a>=12.0&&a<17.0))return"SPRINGZEIT";if((a>=5.5&&a<9.5)||(a>=20.5&&a<24.5))return"NIPPZEIT";return"MITTZEIT";}

    private String[] sunTimes(){
        if(last==null)return new String[]{"—","—","—"}; Calendar cal=Calendar.getInstance();int day=cal.get(Calendar.DAY_OF_YEAR);
        double lat=last.getLatitude(),lon=last.getLongitude(),z=90.8333,lh=lon/15.0;
        return new String[]{solarTime(day,lat,lh,z,true,cal.getTimeZone()),solarTime(day,lat,lh,z,false,cal.getTimeZone()),daylightLength(day,lat,z)};
    }
    private String solarTime(double n,double lat,double lh,double z,boolean rising,TimeZone tz){
        double t=n+((rising?6:18)-lh)/24.0,M=.9856*t-3.289,L=(M+1.916*Math.sin(Math.toRadians(M))+.020*Math.sin(Math.toRadians(2*M))+282.634)%360;if(L<0)L+=360;
        double ra=Math.toDegrees(Math.atan(.91764*Math.tan(Math.toRadians(L))));ra=(ra+360)%360;ra+=Math.floor(L/90)*90-Math.floor(ra/90)*90;ra/=15;
        double sd=.39782*Math.sin(Math.toRadians(L)),cd=Math.cos(Math.asin(sd)),ch=(Math.cos(Math.toRadians(z))-sd*Math.sin(Math.toRadians(lat)))/(cd*Math.cos(Math.toRadians(lat)));if(ch>1||ch<-1)return"—";
        double H=(rising?360-Math.toDegrees(Math.acos(ch)):Math.toDegrees(Math.acos(ch)))/15,T=H+ra-.06571*t-6.622,utc=(T-lh)%24;if(utc<0)utc+=24;
        Calendar out=Calendar.getInstance(tz);double local=(utc+tz.getOffset(out.getTimeInMillis())/3600000.0+24)%24;int hour=(int)local,min=(int)Math.round((local-hour)*60);if(min>=60){min=0;hour=(hour+1)%24;}return String.format(Locale.GERMANY,"%02d:%02d",hour,min);
    }
    private String daylightLength(double n,double lat,double z){double decl=23.45*Math.sin(Math.toRadians(360.0/365.0*(284+n))),ch=(Math.cos(Math.toRadians(z))-Math.sin(Math.toRadians(lat))*Math.sin(Math.toRadians(decl)))/(Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(decl)));if(ch>=1)return"00:00";if(ch<=-1)return"24:00";int m=(int)Math.round(2*Math.toDegrees(Math.acos(ch))*4);return String.format(Locale.GERMANY,"%02d:%02d",m/60,m%60);}

    private float d(float v){return v*getResources().getDisplayMetrics().density;}
    private void fill(int color){p.setStyle(Paint.Style.FILL);p.setColor(color);}
    private void stroke(int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(width));p.setColor(color);}
    private void rr(Canvas c,float l,float t,float r,float b,float rad){c.drawRoundRect(d(l),d(t),d(r),d(b),d(rad),d(rad),p);}
    private void text(Canvas c,String s,float x,float y,float size,Paint.Align a,boolean bold){p.setStyle(Paint.Style.FILL);p.setTextSize(size*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(a);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,d(x),d(y),p);}
    private void card(Canvas c,float l,float t,float r,float b,int fill,int line){fill(fill);rr(c,l,t,r,b,13);stroke(line,1);rr(c,l,t,r,b,13);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float den=getResources().getDisplayMetrics().density,w=getWidth()/den,h=getHeight()/den,side=12,gap=8,center=w/2,bw=(w-2*side-gap)/2;
        int bg=night?Color.rgb(6,11,17):Color.rgb(246,249,251),surface=night?Color.rgb(12,19,27):Color.WHITE,fg=night?Color.rgb(241,231,216):Color.rgb(22,38,52),muted=night?Color.rgb(154,139,122):Color.rgb(101,120,135),accent=night?Color.rgb(222,137,67):Color.rgb(25,126,218),line=night?Color.rgb(51,52,55):Color.rgb(205,216,223);
        fill(bg);c.drawRect(0,0,getWidth(),getHeight(),p);
        fill(fg);text(c,"NAUTICNAV",center,25,12,Paint.Align.CENTER,true);fill(accent);rr(c,center-16,32,center+16,34,2);
        fill(muted);text(c,"GESAMTDISTANZ",center,51,9,Paint.Align.CENTER,true);fill(fg);text(c,String.format(Locale.US,"%.2f sm",distanceNm),center,87,38,Paint.Align.CENTER,true);

        float top=99,cardH=69;card(c,side,top,side+bw,top+cardH,surface,line);card(c,side+bw+gap,top,w-side,top+cardH,surface,line);
        fill(accent);text(c,"SOG AKTUELL",side+bw/2,top+19,10,Paint.Align.CENTER,true);fill(fg);text(c,String.format(Locale.US,"%.1f kn",sog),side+bw/2,top+46,23,Paint.Align.CENTER,true);fill(muted);text(c,String.format(Locale.US,"Ø %.1f kn · letzte 0,5 sm",avgSog()),side+bw/2,top+61,8,Paint.Align.CENTER,false);
        fill(accent);text(c,"COG AKTUELL",side+bw+gap+bw/2,top+19,10,Paint.Align.CENTER,true);fill(fg);text(c,String.format(Locale.US,"%03.0f°",cog),side+bw+gap+bw/2,top+46,23,Paint.Align.CENTER,true);fill(muted);text(c,String.format(Locale.US,"Ø %03.0f° · letzte 0,5 sm",avgCog()),side+bw+gap+bw/2,top+61,8,Paint.Align.CENTER,false);

        top+=cardH+9;card(c,side,top,w-side,top+61,surface,line);fill(fg);text(c,timeFmt.format(new Date()),center,top+21,19,Paint.Align.CENTER,true);fill(muted);text(c,"FAHRTZEIT  "+trip(),center,top+40,10,Paint.Align.CENTER,false);if(showPos)text(c,pos(),center,top+54,8,Paint.Align.CENTER,false);
        top+=69;card(c,side,top,w-side,top+43,surface,line);fill(accent);text(c,"GPS",side+12,top+18,9,Paint.Align.LEFT,true);fill(fg);text(c,sats+" SAT",side+40,top+18,10,Paint.Align.LEFT,true);fill(muted);text(c,last==null?"±— m":String.format(Locale.US,"±%.0f m",last.getAccuracy()),center,top+18,10,Paint.Align.CENTER,false);fill(last!=null&&last.hasAccuracy()?accent:muted);text(c,last!=null&&last.hasAccuracy()?"3D FIX":"NO FIX",w-side-12,top+18,10,Paint.Align.RIGHT,true);

        top+=51;float infoH=73;card(c,side,top,center-4,top+infoH,surface,line);card(c,center+4,top,w-side,top+infoH,surface,line);
        fill(accent);text(c,"MOND",side+12,top+19,10,Paint.Align.LEFT,true);fill(fg);text(c,moonPhase(),side+12,top+39,9,Paint.Align.LEFT,false);fill(accent);text(c,"ALTER DER GEZEIT",side+12,top+56,8,Paint.Align.LEFT,true);fill(night?Color.rgb(235,160,83):Color.rgb(25,105,180));text(c,tideAge(),side+12,top+68,9,Paint.Align.LEFT,true);
        fill(accent);text(c,"GEZEITEN",center+10,top+19,10,Paint.Align.LEFT,true);fill(fg);text(c,"HW / NW",center+10,top+40,9,Paint.Align.LEFT,true);fill(muted);text(c,"Tidedatenquelle",center+10,top+57,8,Paint.Align.LEFT,false);text(c,"für Standort",center+10,top+68,8,Paint.Align.LEFT,false);

        top+=infoH+8;card(c,side,top,w-side,top+38,surface,line);String[] sun=sunTimes();fill(accent);text(c,"SONNE",side+9,top+24,9,Paint.Align.LEFT,true);fill(fg);text(c,"↑ "+sun[0],side+78,top+24,9,Paint.Align.LEFT,true);text(c,"↓ "+sun[1],center+5,top+24,9,Paint.Align.LEFT,true);fill(muted);text(c,"TAG "+sun[2],w-side-9,top+24,8,Paint.Align.RIGHT,false);
        top+=46;float ctrlH=42;card(c,side,top,center-4,top+ctrlH,surface,line);card(c,center+4,top,w-side,top+ctrlH,surface,line);fill(fg);text(c,night?"☀  TAGMODUS":"☾  NACHTMODUS",center/2,top+27,10,Paint.Align.CENTER,true);text(c,"HELLIGKEIT  "+Math.round(brightness*100)+"%",center+(w-center)/2,top+27,10,Paint.Align.CENTER,true);
        float resetH=54,rb=h-resetH-10;fill(accent);rr(c,side,rb,w-side,rb+resetH,16);fill(Color.WHITE);text(c,"↻  RESET · GESAMTDISTANZ & FAHRTZEIT",center,rb+33,12,Paint.Align.CENTER,true);
    }

    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float den=getResources().getDisplayMetrics().density,x=e.getX()/den,y=e.getY()/den,w=getWidth()/den,h=getHeight()/den,center=w/2,side=12,resetH=54,rb=h-resetH-10;if(y>=rb&&y<=rb+resetH){distanceNm=0;startMs=0;history.clear();last=null;sog=0;cog=0;invalidate();return true;}float ctrlTop=rb-46;if(y>=ctrlTop&&y<=ctrlTop+42){if(x<center)night=!night;else{brightness+=.15f;if(brightness>1)brightness=.25f;if(getContext() instanceof MainActivity)((MainActivity)getContext()).setBrightness(brightness);}invalidate();}return true;}
}
