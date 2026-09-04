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
    private boolean night = false;
    private int satsUsed = 0, satsTotal = 0;
    private float brightness = 0.65f;
    private double distanceNm = 0, sog = 0, cog = 0;
    private long startMs = 0;
    private Location last;
    private final ArrayDeque<Point> history = new ArrayDeque<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY);
    private float den;
    private float controlTopDp = 0, resetTopDp = 0;

    private static class Point { double sog, cog, seg; Point(double s,double c,double g){sog=s;cog=c;seg=g;} }
    public NavView(Context c){ super(c); setFocusable(true); den=getResources().getDisplayMetrics().density; }

    public void setSatellites(int used, int total){ satsUsed=used; satsTotal=total; invalidate(); }

    public void updateLocation(Location l){
        if(startMs==0) startMs=System.currentTimeMillis();
        double seg=last==null?0:last.distanceTo(l)/1852.0;
        distanceNm+=seg;
        sog=l.hasSpeed()?Math.max(0,l.getSpeed()*1.943844):0;
        if(l.hasBearing()) cog=(l.getBearing()+360)%360;
        history.addLast(new Point(sog,cog,seg));
        while(totalHistory()>0.5 && history.size()>1) history.removeFirst();
        last=new Location(l);
        invalidate();
    }
    private double totalHistory(){double s=0;for(Point x:history)s+=x.seg;return s;}
    private double avgSog(){if(history.isEmpty())return 0;double v=0;for(Point x:history)v+=x.sog;return v/history.size();}
    private double avgCog(){if(history.isEmpty())return 0;double x=0,y=0;for(Point a:history){double r=Math.toRadians(a.cog);x+=Math.sin(r);y+=Math.cos(r);}return(Math.toDegrees(Math.atan2(x,y))+360)%360;}
    private String pos(){return last==null?"Position wartet auf GPS":String.format(Locale.US,"%.5f° N   %.5f° E",last.getLatitude(),last.getLongitude());}
    private String trip(){long s=startMs==0?0:(System.currentTimeMillis()-startMs)/1000;return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);}

    private double moonAgeDays(){double d=(System.currentTimeMillis()-947182440000L)/86400000.0,a=d%29.530588853;return a<0?a+29.530588853:a;}
    private String moonPhase(){double a=moonAgeDays();if(a<1.85||a>=27.68)return"Neumond";if(a<7.38)return"Zunehmende Sichel";if(a<9.23)return"Erstes Viertel";if(a<14.77)return"Zunehmender Mond";if(a<16.62)return"Vollmond";if(a<22.15)return"Abnehmender Mond";if(a<24.00)return"Letztes Viertel";return"Abnehmende Sichel";}
    private String tideAge(){double a=moonAgeDays();if(a<2.5||a>=27.0||(a>=12.0&&a<17.0))return"SPRINGZEIT";if((a>=5.5&&a<9.5)||(a>=20.5&&a<24.5))return"NIPPZEIT";return"MITTZEIT";}

    private String[] sunTimes(){
        if(last==null)return new String[]{"—","—","—"};
        Calendar cal=Calendar.getInstance(); int day=cal.get(Calendar.DAY_OF_YEAR);
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

    private float d(float v){return v*den;}
    private void fill(int color){p.setStyle(Paint.Style.FILL);p.setColor(color);}
    private void stroke(int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(width));p.setColor(color);}
    private void rr(Canvas c,float l,float t,float r,float b,float rad){c.drawRoundRect(d(l),d(t),d(r),d(b),d(rad),d(rad),p);}
    private void text(Canvas c,String s,float x,float y,float size,Paint.Align a,boolean bold){p.setStyle(Paint.Style.FILL);p.setTextSize(size*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(a);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,d(x),d(y),p);}
    private void card(Canvas c,float l,float t,float r,float b,int fillColor,int line){fill(fillColor);rr(c,l,t,r,b,13);stroke(line,1);rr(c,l,t,r,b,13);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth()/den,h=getHeight()/den,side=12,gap=8,center=w/2,bw=(w-2*side-gap)/2;
        int bg=night?Color.rgb(5,11,18):Color.rgb(246,249,251),surface=night?Color.rgb(9,20,31):Color.WHITE;
        int fg=night?Color.rgb(242,246,250):Color.rgb(20,35,49),muted=night?Color.rgb(160,176,190):Color.rgb(105,121,133);
        int accent=Color.rgb(25,126,218),green=Color.rgb(28,178,66),yellow=Color.rgb(235,175,20),line=night?Color.rgb(28,69,104):Color.rgb(205,216,223);
        fill(bg);c.drawRect(0,0,getWidth(),getHeight(),p);

        // Header
        fill(accent);text(c,"⚓",side+18,29,18,Paint.Align.CENTER,false);fill(fg);text(c,"NAUTICNAV",center,27,13,Paint.Align.CENTER,true);fill(accent);text(c,"☰",w-side-12,30,20,Paint.Align.CENTER,false);

        // Distance
        fill(accent);text(c,"GESAMTDISTANZ",center,53,9,Paint.Align.CENTER,true);fill(fg);text(c,String.format(Locale.US,"%.2f",distanceNm),center-8,88,39,Paint.Align.RIGHT,true);fill(muted);text(c,"sm",center+6,88,20,Paint.Align.LEFT,true);

        float top=101,cardH=67;
        card(c,side,top,side+bw,top+cardH,surface,line);card(c,side+bw+gap,top,w-side,top+cardH,surface,line);
        fill(accent);text(c,"SOG AKTUELL",side+bw/2,top+19,10,Paint.Align.CENTER,true);fill(fg);text(c,String.format(Locale.US,"%.2f kn",sog),side+bw/2,top+45,21,Paint.Align.CENTER,true);fill(muted);text(c,String.format(Locale.US,"Ø %.2f kn",avgSog()),side+bw/2-22,top+60,8,Paint.Align.RIGHT,false);text(c,"· letzte 0,5 sm",side+bw/2-17,top+60,8,Paint.Align.LEFT,false);
        fill(accent);text(c,"COG AKTUELL",side+bw+gap+bw/2,top+19,10,Paint.Align.CENTER,true);fill(fg);text(c,String.format(Locale.US,"%03.0f° T",cog),side+bw+gap+bw/2,top+45,21,Paint.Align.CENTER,true);fill(muted);text(c,String.format(Locale.US,"Ø %03.0f°",avgCog()),side+bw+gap+bw/2-20,top+60,8,Paint.Align.RIGHT,false);text(c,"· letzte 0,5 sm",side+bw+gap+bw/2-15,top+60,8,Paint.Align.LEFT,false);

        // Time / trip / position
        top+=cardH+9;card(c,side,top,w-side,top+62,surface,line);fill(fg);text(c,timeFmt.format(new Date()),center,top+23,19,Paint.Align.CENTER,true);fill(muted);text(c,"FAHRTZEIT",center-8,top+42,9,Paint.Align.RIGHT,true);text(c,trip(),center+4,top+42,9,Paint.Align.LEFT,false);text(c,pos(),center,top+56,8,Paint.Align.CENTER,false);

        // GPS
        top+=70;card(c,side,top,w-side,top+44,surface,line);fill(accent);text(c,"GPS",side+10,top+18,9,Paint.Align.LEFT,true);fill(fg);text(c,satsUsed+" / "+satsTotal+" SAT",side+42,top+18,10,Paint.Align.LEFT,true);fill(muted);text(c,last==null?"±— m":String.format(Locale.US,"±%.1f m",last.getAccuracy()),center,top+18,9,Paint.Align.CENTER,false);fill(last!=null&&last.hasAccuracy()?green:muted);text(c,last!=null&&last.hasAccuracy()?"3D FIX":"NO FIX",w-side-10,top+18,9,Paint.Align.RIGHT,true);

        // Moon / tide
        top+=52;float infoH=73;card(c,side,top,center-4,top+infoH,surface,line);card(c,center+4,top,w-side,top+infoH,surface,line);
        fill(accent);text(c,"MOND",side+12,top+19,10,Paint.Align.LEFT,true);fill(fg);text(c,moonPhase(),side+12,top+39,9,Paint.Align.LEFT,false);fill(accent);text(c,"TIDENALTER",side+12,top+56,8,Paint.Align.LEFT,true);fill(yellow);text(c,tideAge(),side+12,top+68,9,Paint.Align.LEFT,true);
        fill(accent);text(c,"GEZEITEN",center+10,top+19,10,Paint.Align.LEFT,true);fill(fg);text(c,"HW / NW",center+10,top+39,9,Paint.Align.LEFT,true);fill(muted);text(c,"Lokale Tidedaten erforderlich",center+10,top+57,7,Paint.Align.LEFT,false);text(c,"GPS allein liefert keine Zeiten/Höhen",center+10,top+68,6.5f,Paint.Align.LEFT,false);

        // Sun line
        top+=infoH+8;card(c,side,top,w-side,top+39,surface,line);String[] sun=sunTimes();fill(yellow);text(c,"☀  SONNE",side+10,top+25,9,Paint.Align.LEFT,true);fill(fg);text(c,"AUFGANG  "+sun[0],center-4,top+25,8,Paint.Align.RIGHT,true);text(c,"UNTERGANG  "+sun[1],center+4,top+25,8,Paint.Align.LEFT,true);fill(muted);text(c,"TAG "+sun[2],w-side-10,top+25,8,Paint.Align.RIGHT,false);

        // Controls, deliberately large touch targets
        top+=47;controlTopDp=top;float ctrlH=52;card(c,side,top,center-4,top+ctrlH,surface,line);card(c,center+4,top,w-side,top+ctrlH,surface,line);
        fill(fg);text(c,night?"☀  TAGMODUS":"☾  NACHTMODUS",(side+center-4)/2,top+22,10,Paint.Align.CENTER,true);fill(accent);text(c,"ANTIPPEN",(side+center-4)/2,top+39,7,Paint.Align.CENTER,false);
        fill(fg);text(c,"HELLIGKEIT  "+Math.round(brightness*100)+"%",center+(w-center)/2,top+20,10,Paint.Align.CENTER,true);
        float slL=center+18,slR=w-side-18,slY=top+38;stroke(night?Color.rgb(80,100,120):Color.rgb(190,205,214),3);c.drawLine(d(slL),d(slY),d(slR),d(slY),p);fill(accent);c.drawCircle(d(slL+(slR-slL)*brightness),d(slY),d(6),p);

        // Reset at bottom
        resetTopDp=h-62;fill(accent);rr(c,side,resetTopDp,w-side,h-10,16);fill(Color.WHITE);text(c,"↻  RESET",center,resetTopDp+23,12,Paint.Align.CENTER,true);text(c,"Gesamtdistanz & Fahrzeit",center,resetTopDp+42,8,Paint.Align.CENTER,false);
    }

    private void reset(){distanceNm=0;startMs=0;history.clear();last=null;sog=0;cog=0;invalidate();}
    private void setNight(boolean value){night=value;if(getContext() instanceof MainActivity)((MainActivity)getContext()).setNightMode(night);invalidate();}
    private void setBrightnessFromX(float x){float left=(getWidth()/den)/2+18,right=getWidth()/den-30;brightness=Math.max(.10f,Math.min(1f,(x-left)/(right-left)));if(getContext() instanceof MainActivity)((MainActivity)getContext()).setBrightness(brightness);invalidate();}

    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX()/den,y=e.getY()/den,w=getWidth()/den,h=getHeight()/den,center=w/2;
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            if(y>=resetTopDp){reset();return true;}
            if(y>=controlTopDp && y<=controlTopDp+52){
                if(x<center){setNight(!night);} else setBrightnessFromX(x);
                return true;
            }
            return true;
        }
        if((e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP) && y>=controlTopDp && y<=controlTopDp+60 && x>=center){setBrightnessFromX(x);return true;}
        return true;
    }
}
