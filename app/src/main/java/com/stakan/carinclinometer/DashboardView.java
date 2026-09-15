package com.stakan.carinclinometer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;
import java.util.Calendar;
import java.util.TimeZone;

public class DashboardView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final Bitmap[] vehicleFront = new Bitmap[4];
    private final Bitmap[] vehicleSide = new Bitmap[4];
    private final ToneGenerator alarmTone = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
    private final Vibrator vibrator;
    private float rawRoll, rawPitch, roll, pitch, heading;
    private float zeroRoll, zeroPitch;
    private float shownRoll, shownPitch;
    private double latitude, longitude, altitude;
    private float speedKmh, gpsAccuracy;
    private boolean hasGps, gpsDenied;
    private int theme;
    private int vehicle;
    private int alarmSound;
    private boolean muted;
    private boolean autoDayNight;
    private boolean swapAxes;
    private boolean invertRoll;
    private boolean invertPitch;
    private int language;
    private boolean daylight;
    private long lastSolarCheck;
    private long lastFrame;
    private long lastAlarm;
    private static final float ROLL_LIMIT = 30f;
    private static final float PITCH_LIMIT = 25f;
    private RectF calibrateButton = new RectF(), themeButton = new RectF(), vehicleButton = new RectF(), muteButton = new RectF(), soundButton = new RectF(), autoButton = new RectF(), aboutButton = new RectF(), helpButton = new RectF();
    private final int[][] palettes = {
            {0xFF050B12, 0xFF0B1822, 0xFF00E5FF, 0xFF69FF8C, 0xFFFFB300, 0xFFF3FAFF},
            {0xFF090806, 0xFF1C1308, 0xFFFF9700, 0xFFFFD54F, 0xFFFF4D24, 0xFFFFF1D0},
            {0xFF071018, 0xFF102637, 0xFF8BE9FD, 0xFFA8D8FF, 0xFFFF6B8A, 0xFFF5FBFF}
    };
    private final String[] themeNames = {"NEON", "AMBER", "ICE"};
    private final String[] vehicleNames = {"SEDAN", "HUMMER", "TANK", "BUGGY"};
    private final String[] soundNames = {"BEEP", "SIREN", "ALERT", "DOUBLE"};
    private final int[] soundTones = {ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ToneGenerator.TONE_PROP_BEEP2};

    public DashboardView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        prefs = context.getSharedPreferences("dashboard", Context.MODE_PRIVATE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        zeroRoll = prefs.getFloat("zeroRoll", 0f);
        zeroPitch = prefs.getFloat("zeroPitch", 0f);
        theme = prefs.getInt("theme", 0);
        vehicle = prefs.getInt("vehicle", 0);
        alarmSound = prefs.getInt("alarmSound", 0);
        muted = prefs.getBoolean("muted", false);
        autoDayNight = prefs.getBoolean("autoDayNight", true);
        swapAxes = prefs.getBoolean("swapAxes", false);
        invertRoll = prefs.getBoolean("invertRoll", false);
        invertPitch = prefs.getBoolean("invertPitch", false);
        language = prefs.getInt("language", 0);
        vehicleFront[0]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_sedan_front);
        vehicleSide[0]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_sedan_side);
        vehicleFront[1]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_hummer_front);
        vehicleSide[1]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_hummer_side);
        vehicleFront[2]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_tank_front);
        vehicleSide[2]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_tank_side);
        vehicleFront[3]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_buggy_front);
        vehicleSide[3]=BitmapFactory.decodeResource(getResources(),R.drawable.vehicle_buggy_side);
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
    }

    public void updateAttitude(float r, float pt, float h) {
        if(swapAxes){float value=r;r=pt;pt=value;}
        if(invertRoll)r=-r;
        if(invertPitch)pt=-pt;
        rawRoll = normalize(r); rawPitch = normalize(pt); heading = h;
        roll = clamp(normalize(rawRoll - zeroRoll), -60, 60);
        pitch = clamp(normalize(rawPitch - zeroPitch), -60, 60);
        postInvalidateOnAnimation();
    }

    public void updateLocation(double lat, double lon, float speed, double alt, float accuracy) {
        latitude = lat; longitude = lon; speedKmh = Math.max(0, speed); altitude = alt; gpsAccuracy = accuracy;
        hasGps = true; gpsDenied = false; applyAutomaticTheme(); postInvalidateOnAnimation();
    }
    public void setGpsAvailable(boolean value) { hasGps = value; postInvalidateOnAnimation(); }
    public void setGpsDenied(boolean value) { gpsDenied = value; postInvalidateOnAnimation(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = System.nanoTime();
        if(autoDayNight&&hasGps&&System.currentTimeMillis()-lastSolarCheck>60000)applyAutomaticTheme();
        float dt = lastFrame == 0 ? .016f : Math.min(.05f, (now-lastFrame)/1_000_000_000f);
        lastFrame = now;
        float smoothing = 1f - (float)Math.pow(.035, dt);
        shownRoll += (roll - shownRoll) * smoothing;
        shownPitch += (pitch - shownPitch) * smoothing;

        int w = getWidth(), h = getHeight();
        int[] col = palettes[theme];
        p.setShader(new LinearGradient(0,0,w,h,col[0],col[1], Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p); p.setShader(null);
        drawGrid(c,w,h,col);

        float top = h*.095f, bottom = h*.82f;
        float radius = Math.min(h*.34f, w*.205f);
        float leftX = w*.265f, rightX = w*.735f, cy = (top+bottom)*.5f;
        // Roll is side-to-side, so show the vehicle from the front.
        drawGauge(c,leftX,cy,radius,shownRoll,tr("ROLL","КРЕН","גלגול"),col,false);
        // Pitch is nose-up / nose-down, so show the vehicle from the side.
        drawGauge(c,rightX,cy,radius,shownPitch,tr("PITCH","ТАНГАЖ","עלרוד"),col,true);
        drawCenter(c,w*.5f,cy,radius*.64f,col);
        drawBottom(c,w,h,col);
        drawAlarm(c,w,h,col);
        postInvalidateOnAnimation();
    }

    private void drawAlarm(Canvas c,int w,int h,int[] col) {
        boolean rollDanger=Math.abs(shownRoll)>=ROLL_LIMIT;
        boolean pitchDanger=Math.abs(shownPitch)>=PITCH_LIMIT;
        if(!rollDanger&&!pitchDanger)return;
        long now=System.currentTimeMillis();
        int pulse=(int)(120+100*Math.abs(Math.sin(now/180.0)));
        p.setColor(Color.argb(pulse,255,25,20));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(8,h*.018f));c.drawRoundRect(new RectF(10,10,w-10,h-10),22,22,p);p.setStyle(Paint.Style.FILL);
        RectF warning=new RectF(w*.315f,h*.105f,w*.685f,h*.205f);p.setColor(0xE6B00000);c.drawRoundRect(warning,16,16,p);
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setTextSize(h*.043f);p.setColor(Color.WHITE);
        String message=rollDanger&&pitchDanger?tr("DANGER: ROLL + PITCH","ОПАСНО: КРЕН + ТАНГАЖ","סכנה: גלגול + עלרוד"):rollDanger?tr("ROLL WARNING","ОПАСНЫЙ КРЕН","אזהרת גלגול"):tr("PITCH WARNING","ОПАСНЫЙ ТАНГАЖ","אזהרת עלרוד");
        c.drawText(message,w*.5f,warning.centerY()+h*.015f,p);p.setFakeBoldText(false);
        if(now-lastAlarm>1800){lastAlarm=now;if(!muted)alarmTone.startTone(soundTones[alarmSound],alarmSound==3?700:450);if(vibrator!=null&&vibrator.hasVibrator()){if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,180,90,180},-1));else vibrator.vibrate(new long[]{0,180,90,180},-1);}}
    }

    private void drawGrid(Canvas c,int w,int h,int[] col) {
        p.setColor(withAlpha(col[2],20)); p.setStrokeWidth(1);
        for(int x=0;x<w;x+=Math.max(24,w/30)) c.drawLine(x,0,x,h,p);
        for(int y=0;y<h;y+=Math.max(24,h/18)) c.drawLine(0,y,w,y,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(withAlpha(col[2],65));
        c.drawRoundRect(new RectF(8,8,w-8,h-8),22,22,p); p.setStyle(Paint.Style.FILL);
    }

    private void drawGauge(Canvas c,float cx,float cy,float r,float value,String label,int[] col,boolean sideView) {
        glow.setStyle(Paint.Style.FILL); glow.setColor(withAlpha(col[2],18)); glow.setShadowLayer(28,0,0,col[2]);
        c.drawCircle(cx,cy,r,glow); glow.clearShadowLayer();
        p.setColor(0xD9050A0E); c.drawCircle(cx,cy,r*.93f,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r*.035f); p.setColor(col[2]);
        c.drawArc(new RectF(cx-r*.88f,cy-r*.88f,cx+r*.88f,cy+r*.88f),-220,260,false,p);
        p.setStrokeWidth(r*.018f); p.setTextAlign(Paint.Align.CENTER);
        for(int d=-60;d<=60;d+=5) {
            float a=(float)Math.toRadians(90+d*2.15f), outer=r*.86f, inner=r*(d%10==0?.70f:.76f);
            float x1=cx+(float)Math.cos(a)*outer, y1=cy+(float)Math.sin(a)*outer;
            float x2=cx+(float)Math.cos(a)*inner, y2=cy+(float)Math.sin(a)*inner;
            p.setColor(Math.abs(d)>=40?col[4]:(Math.abs(d)>=25?col[3]:col[2]));
            c.drawLine(x1,y1,x2,y2,p);
            if(d%10==0){ p.setStyle(Paint.Style.FILL);p.setTextSize(r*.105f);p.setColor(col[5]);
                c.drawText(String.valueOf(Math.abs(d)),cx+(float)Math.cos(a)*r*.62f,cy+(float)Math.sin(a)*r*.62f+r*.035f,p);p.setStyle(Paint.Style.STROKE);}
        }
        c.save(); c.clipPath(circlePath(cx,cy,r*.56f)); c.rotate(-value,cx,cy);
        p.setStyle(Paint.Style.FILL); p.setColor(0xFF142B3A); c.drawRect(cx-r,cy-r,cx+r,cy,p);
        p.setColor(theme==1?0xFF9D4F15:0xFF98643B); c.drawRect(cx-r,cy,cx+r,cy+r,p);
        p.setColor(col[3]); p.setStrokeWidth(r*.025f); c.drawRect(cx-r,cy-r*.015f,cx+r,cy+r*.015f,p);
        c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.02f);p.setColor(withAlpha(col[5],120));c.drawCircle(cx,cy,r*.56f,p);
        drawVehicle(c,cx,cy,r*.42f,value,col,sideView,vehicle);
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(col[5]);p.setTextSize(r*.20f);p.setFakeBoldText(true);
        c.drawText(String.format(Locale.US,"%+.1f°",value),cx,cy+r*.35f,p);
        p.setTextSize(r*.12f);p.setColor(col[2]);c.drawText(label,cx,cy+r*1.08f,p);p.setFakeBoldText(false);
    }

    private void drawVehicle(Canvas c,float cx,float cy,float s,float angle,int[] col,boolean side,int type) {
        Bitmap sprite=side?vehicleSide[type]:vehicleFront[type];
        if(sprite!=null){
            c.save();c.rotate(-angle,cx,cy);p.setAlpha(255);p.setFilterBitmap(true);
            float halfW=side?s*1.08f:s*.72f,halfH=side?s*.72f:s*.78f;
            c.drawBitmap(sprite,null,new RectF(cx-halfW,cy-halfH,cx+halfW,cy+halfH),p);c.restore();return;
        }
        c.save(); c.rotate(-angle,cx,cy);p.setStyle(Paint.Style.FILL);p.setColor(col[5]);
        if(side){
            if(type==2){
                Path track=new Path();track.moveTo(cx-s*.76f,cy-s*.02f);track.lineTo(cx-s*.60f,cy-s*.25f);track.lineTo(cx+s*.62f,cy-s*.25f);track.lineTo(cx+s*.78f,cy-s*.02f);track.lineTo(cx+s*.62f,cy+s*.34f);track.lineTo(cx-s*.61f,cy+s*.34f);track.close();c.drawPath(track,p);
                Path hull=new Path();hull.moveTo(cx-s*.72f,cy-s*.24f);hull.lineTo(cx-s*.42f,cy-s*.48f);hull.lineTo(cx+s*.60f,cy-s*.36f);hull.lineTo(cx+s*.70f,cy-s*.22f);hull.close();c.drawPath(hull,p);
                Path turret=new Path();turret.moveTo(cx-s*.30f,cy-s*.48f);turret.lineTo(cx+s*.02f,cy-s*.75f);turret.lineTo(cx+s*.42f,cy-s*.68f);turret.lineTo(cx+s*.58f,cy-s*.45f);turret.lineTo(cx+s*.22f,cy-s*.38f);turret.close();c.drawPath(turret,p);
                c.drawRect(cx-s*1.02f,cy-s*.65f,cx-s*.12f,cy-s*.57f,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.028f);c.drawLine(cx+s*.34f,cy-s*.66f,cx+s*.80f,cy-s*.75f,p);c.drawLine(cx+s*.35f,cy-s*.55f,cx+s*.80f,cy-s*.62f,p);p.setStyle(Paint.Style.FILL);
                p.setColor(col[0]);for(int i=0;i<6;i++)c.drawCircle(cx-s*.56f+i*s*.225f,cy+s*.16f,s*.115f,p);
                p.setColor(col[2]);for(int i=0;i<6;i++)c.drawCircle(cx-s*.56f+i*s*.225f,cy+s*.16f,s*.045f,p);
            } else if(type==3){
                Path buggy=new Path();buggy.moveTo(cx-s*.82f,cy-s*.10f);buggy.lineTo(cx-s*.72f,cy-s*.48f);buggy.lineTo(cx-s*.20f,cy-s*.62f);buggy.lineTo(cx-s*.04f,cy-s*.30f);buggy.lineTo(cx+s*.25f,cy-s*.18f);buggy.lineTo(cx+s*.76f,cy-s*.10f);buggy.lineTo(cx+s*.84f,cy+s*.08f);buggy.lineTo(cx+s*.65f,cy+s*.20f);buggy.lineTo(cx+s*.45f,cy+s*.05f);buggy.lineTo(cx-s*.38f,cy+s*.16f);buggy.lineTo(cx-s*.58f,cy+s*.02f);buggy.close();c.drawPath(buggy,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.045f);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(cx-s*.18f,cy-s*.60f,cx+s*.10f,cy-s*.62f,p);c.drawLine(cx+s*.10f,cy-s*.62f,cx+s*.35f,cy-s*.20f,p);c.drawLine(cx-s*.05f,cy-s*.31f,cx-s*.18f,cy-s*.60f,p);p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
                p.setColor(col[0]);c.drawCircle(cx-s*.52f,cy+s*.20f,s*.25f,p);c.drawCircle(cx+s*.53f,cy+s*.20f,s*.25f,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.022f);p.setColor(col[2]);for(int i=0;i<4;i++)c.drawLine(cx-s*(.68f-i*.11f),cy-s*.40f,cx-s*(.62f-i*.11f),cy-s*.18f,p);p.setStyle(Paint.Style.FILL);
                p.setColor(col[2]);c.drawCircle(cx-s*.52f,cy+s*.20f,s*.09f,p);c.drawCircle(cx+s*.53f,cy+s*.20f,s*.09f,p);
            } else {
                if(type==1){
                    Path hummer=new Path();hummer.moveTo(cx-s*.84f,cy+s*.12f);hummer.lineTo(cx-s*.80f,cy-s*.12f);hummer.lineTo(cx-s*.60f,cy-s*.24f);hummer.lineTo(cx-s*.45f,cy-s*.60f);hummer.lineTo(cx+s*.53f,cy-s*.60f);hummer.lineTo(cx+s*.68f,cy-s*.30f);hummer.lineTo(cx+s*.80f,cy-s*.24f);hummer.lineTo(cx+s*.80f,cy+s*.18f);hummer.lineTo(cx-s*.72f,cy+s*.18f);hummer.close();c.drawPath(hummer,p);
                    p.setColor(col[0]);c.drawRect(cx-s*.35f,cy-s*.50f,cx-s*.08f,cy-s*.24f,p);c.drawRect(cx,cy-s*.50f,cx+s*.28f,cy-s*.24f,p);c.drawRect(cx+s*.36f,cy-s*.50f,cx+s*.57f,cy-s*.24f,p);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.018f);p.setColor(col[2]);c.drawLine(cx-s*.05f,cy-s*.20f,cx-s*.05f,cy+s*.08f,p);c.drawLine(cx+s*.32f,cy-s*.20f,cx+s*.32f,cy+s*.08f,p);p.setStyle(Paint.Style.FILL);
                    p.setColor(col[5]);c.drawCircle(cx+s*.82f,cy-s*.05f,s*.16f,p);
                    p.setColor(col[0]);c.drawCircle(cx-s*.53f,cy+s*.20f,s*.23f,p);c.drawCircle(cx+s*.52f,cy+s*.20f,s*.23f,p);
                    p.setColor(col[2]);c.drawCircle(cx-s*.53f,cy+s*.20f,s*.09f,p);c.drawCircle(cx+s*.52f,cy+s*.20f,s*.09f,p);
                }else{
                    RectF body=new RectF(cx-s*.75f,cy-s*.16f,cx+s*.72f,cy+s*.25f);c.drawRoundRect(body,s*.12f,s*.12f,p);
                    Path roof=new Path();roof.moveTo(cx-s*.38f,cy-s*.16f);roof.lineTo(cx-s*.22f,cy-s*.45f);roof.lineTo(cx+s*.40f,cy-s*.45f);roof.lineTo(cx+s*.62f,cy-s*.16f);roof.close();c.drawPath(roof,p);
                    p.setColor(col[0]);c.drawCircle(cx-s*.43f,cy+s*.25f,s*.18f,p);c.drawCircle(cx+s*.43f,cy+s*.25f,s*.18f,p);
                    p.setColor(col[2]);c.drawCircle(cx-s*.43f,cy+s*.25f,s*.08f,p);c.drawCircle(cx+s*.43f,cy+s*.25f,s*.08f,p);
                }
            }
        }else{
            if(type==2){
                p.setColor(col[5]);c.drawRoundRect(new RectF(cx-s*.72f,cy-s*.18f,cx-s*.48f,cy+s*.48f),s*.05f,s*.05f,p);c.drawRoundRect(new RectF(cx+s*.48f,cy-s*.18f,cx+s*.72f,cy+s*.48f),s*.05f,s*.05f,p);
                p.setColor(withAlpha(col[5],225));Path tankFront=new Path();tankFront.moveTo(cx-s*.48f,cy+s*.38f);tankFront.lineTo(cx-s*.45f,cy-s*.18f);tankFront.lineTo(cx-s*.28f,cy-s*.34f);tankFront.lineTo(cx+s*.28f,cy-s*.34f);tankFront.lineTo(cx+s*.45f,cy-s*.18f);tankFront.lineTo(cx+s*.48f,cy+s*.38f);tankFront.close();c.drawPath(tankFront,p);
                Path wedge=new Path();wedge.moveTo(cx-s*.45f,cy-s*.34f);wedge.lineTo(cx-s*.18f,cy-s*.65f);wedge.lineTo(cx+s*.20f,cy-s*.65f);wedge.lineTo(cx+s*.46f,cy-s*.34f);wedge.lineTo(cx+s*.28f,cy-s*.08f);wedge.lineTo(cx-s*.28f,cy-s*.08f);wedge.close();c.drawPath(wedge,p);
                c.drawRect(cx-s*.045f,cy-s*.98f,cx+s*.045f,cy-s*.48f,p);p.setColor(col[2]);c.drawCircle(cx,cy-s*.40f,s*.055f,p);
            }
            else if(type==3){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.048f);c.drawLine(cx-s*.50f,cy-s*.48f,cx+s*.50f,cy+s*.34f,p);c.drawLine(cx+s*.50f,cy-s*.48f,cx-s*.50f,cy+s*.34f,p);c.drawLine(cx-s*.50f,cy-s*.48f,cx+s*.50f,cy-s*.48f,p);p.setStyle(Paint.Style.FILL);Path nose=new Path();nose.moveTo(cx-s*.62f,cy-s*.04f);nose.lineTo(cx+s*.62f,cy-s*.04f);nose.lineTo(cx+s*.48f,cy+s*.38f);nose.lineTo(cx-s*.48f,cy+s*.38f);nose.close();c.drawPath(nose,p);}
            else {float wide=type==1?.66f:.52f;c.drawRoundRect(new RectF(cx-s*wide,cy-s*(type==1?.52f:.42f),cx+s*wide,cy+s*.45f),s*(type==1?.08f:.16f),s*(type==1?.08f:.16f),p);p.setColor(col[0]);c.drawRoundRect(new RectF(cx-s*.40f,cy-s*.36f,cx+s*.40f,cy-s*.02f),s*.04f,s*.04f,p);if(type==1){p.setColor(col[5]);c.drawRect(cx-s*.05f,cy-s*.36f,cx+s*.02f,cy-s*.02f,p);}}
            p.setColor(col[2]);c.drawCircle(cx-s*.3f,cy+s*.32f,s*.07f,p);c.drawCircle(cx+s*.3f,cy+s*.32f,s*.07f,p);
        } c.restore();
    }

    private void drawCenter(Canvas c,float cx,float cy,float r,int[] col) {
        p.setStyle(Paint.Style.FILL);p.setColor(0xEE071018);c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.045f);p.setColor(col[2]);c.drawCircle(cx,cy,r,p);
        c.save();c.rotate(-heading,cx,cy);p.setStrokeWidth(r*.025f);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r*.16f);
        String[][] directionNames={{"N","E","S","W"},{"С","В","Ю","З"},{"צ","מז","ד","מע"}};String[] dirs=directionNames[language];for(int i=0;i<4;i++){float a=(float)Math.toRadians(i*90-90);p.setColor(i==0?col[4]:col[5]);c.drawText(dirs[i],cx+(float)Math.cos(a)*r*.68f,cy+(float)Math.sin(a)*r*.68f+r*.055f,p);}c.restore();
        Path arrow=new Path();arrow.moveTo(cx,cy-r*.58f);arrow.lineTo(cx-r*.11f,cy);arrow.lineTo(cx+r*.11f,cy);arrow.close();p.setStyle(Paint.Style.FILL);p.setColor(col[4]);c.drawPath(arrow,p);
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setTextSize(r*.27f);p.setColor(col[5]);c.drawText(String.format(Locale.US,"%03.0f°",heading),cx,cy+r*.42f,p);p.setFakeBoldText(false);
    }

    private void drawBottom(Canvas c,int w,int h,int[] col) {
        float y=h*.855f, boxH=h*.11f, gap=w*.012f;
        float widths=(w-gap*6)/5f;
        drawInfo(c,new RectF(gap,y,gap+widths,y+boxH),tr("SPEED","СКОРОСТЬ","מהירות"),hasGps?String.format(Locale.US,"%.0f km/h",speedKmh):"-- km/h",col);
        drawInfo(c,new RectF(gap*2+widths,y,gap*2+widths*2,y+boxH),tr("LATITUDE","ШИРОТА","קו רוחב"),hasGps?String.format(Locale.US,"%.5f",latitude):gpsDenied?tr("PERMISSION","РАЗРЕШЕНИЕ","הרשאה"):tr("SEARCHING","ПОИСК","מחפש"),col);
        drawInfo(c,new RectF(gap*3+widths*2,y,gap*3+widths*3,y+boxH),tr("LONGITUDE","ДОЛГОТА","קו אורך"),hasGps?String.format(Locale.US,"%.5f",longitude):"--",col);
        drawInfo(c,new RectF(gap*4+widths*3,y,gap*4+widths*4,y+boxH),tr("ALTITUDE","ВЫСОТА","גובה"),hasGps?String.format(Locale.US,"%.0f m",altitude):"-- m",col);
        calibrateButton.set(gap*5+widths*4,y,gap*5+widths*5,y+boxH);
        p.setColor(withAlpha(col[2],35));c.drawRoundRect(calibrateButton,12,12,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(col[2]);c.drawRoundRect(calibrateButton,12,12,p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setTextSize(h*.032f);p.setColor(col[2]);c.drawText(tr("CALIBRATE","КАЛИБРОВКА","כיול"),calibrateButton.centerX(),calibrateButton.centerY()+h*.012f,p);p.setFakeBoldText(false);
        themeButton.set(w*.29f,h*.02f,w*.415f,h*.08f);p.setColor(withAlpha(col[2],35));c.drawRoundRect(themeButton,30,30,p);p.setTextSize(h*.022f);p.setColor(col[3]);c.drawText(tr("THEME: ","ТЕМА: ","ערכת נושא: ")+themeName(),themeButton.centerX(),h*.058f,p);
        vehicleButton.set(w*.425f,h*.02f,w*.56f,h*.08f);p.setColor(withAlpha(col[3],35));c.drawRoundRect(vehicleButton,30,30,p);p.setColor(col[3]);c.drawText(tr("VEHICLE: ","ТРАНСПОРТ: ","רכב: ")+vehicleName(),vehicleButton.centerX(),h*.058f,p);
        soundButton.set(w*.57f,h*.02f,w*.705f,h*.08f);p.setColor(withAlpha(col[2],35));c.drawRoundRect(soundButton,30,30,p);p.setColor(col[2]);c.drawText(tr("SOUND: ","ЗВУК: ","צליל: ")+soundName(),soundButton.centerX(),h*.058f,p);
        muteButton.set(w*.715f,h*.02f,w*.82f,h*.08f);p.setColor(muted?0x99A00000:withAlpha(col[3],35));c.drawRoundRect(muteButton,30,30,p);p.setColor(muted?0xFFFF7068:col[3]);c.drawText(muted?tr("MUTED","БЕЗ ЗВУКА","מושתק"):tr("MUTE","ВЫКЛ. ЗВУК","השתק"),muteButton.centerX(),h*.058f,p);
        autoButton.set(w*.83f,h*.02f,w*.955f,h*.08f);p.setColor(autoDayNight?withAlpha(col[3],60):withAlpha(col[5],24));c.drawRoundRect(autoButton,30,30,p);p.setColor(autoDayNight?col[3]:withAlpha(col[5],150));c.drawText(autoDayNight?(daylight?tr("AUTO: DAY","АВТО: ДЕНЬ","אוטו: יום"):tr("AUTO: NIGHT","АВТО: НОЧЬ","אוטו: לילה")):tr("AUTO: OFF","АВТО: ВЫКЛ","אוטו: כבוי"),autoButton.centerX(),h*.058f,p);
        helpButton.set(w*.055f,h*.02f,w*.165f,h*.08f);p.setColor(withAlpha(col[2],35));c.drawRoundRect(helpButton,30,30,p);p.setColor(col[2]);c.drawText(tr("SETTINGS","НАСТРОЙКИ","הגדרות"),helpButton.centerX(),h*.058f,p);
        aboutButton.set(w*.17f,h*.02f,w*.28f,h*.08f);p.setColor(withAlpha(col[2],35));c.drawRoundRect(aboutButton,30,30,p);p.setColor(col[2]);c.drawText(tr("ABOUT","О ПРОГРАММЕ","אודות"),aboutButton.centerX(),h*.058f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(h*.022f);p.setColor(withAlpha(col[5],160));c.drawText(hasGps?String.format(Locale.US,"GPS ±%.0f m",gpsAccuracy):tr("GPS WAITING","ОЖИДАНИЕ GPS","ממתין ל-GPS"),w*.015f,h*.052f,p);
    }

    private void drawInfo(Canvas c,RectF r,String label,String value,int[] col){p.setColor(0xB9060C12);c.drawRoundRect(r,12,12,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(withAlpha(col[2],100));c.drawRoundRect(r,12,12,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r.height()*.22f);p.setColor(withAlpha(col[5],150));c.drawText(label,r.centerX(),r.top+r.height()*.31f,p);p.setTextSize(r.height()*.34f);p.setColor(col[5]);c.drawText(value,r.centerX(),r.top+r.height()*.76f,p);}

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        if(calibrateButton.contains(e.getX(),e.getY())){zeroRoll=rawRoll;zeroPitch=rawPitch;prefs.edit().putFloat("zeroRoll",zeroRoll).putFloat("zeroPitch",zeroPitch).apply();}
        else if(themeButton.contains(e.getX(),e.getY())){autoDayNight=false;theme=(theme+1)%palettes.length;prefs.edit().putBoolean("autoDayNight",false).putInt("theme",theme).apply();}
        else if(vehicleButton.contains(e.getX(),e.getY())){vehicle=(vehicle+1)%vehicleNames.length;prefs.edit().putInt("vehicle",vehicle).apply();}
        else if(soundButton.contains(e.getX(),e.getY())){alarmSound=(alarmSound+1)%soundNames.length;prefs.edit().putInt("alarmSound",alarmSound).apply();if(!muted)alarmTone.startTone(soundTones[alarmSound],350);}
        else if(muteButton.contains(e.getX(),e.getY())){muted=!muted;prefs.edit().putBoolean("muted",muted).apply();if(muted)alarmTone.stopTone();else alarmTone.startTone(soundTones[alarmSound],250);}
        else if(autoButton.contains(e.getX(),e.getY())){autoDayNight=!autoDayNight;if(autoDayNight)applyAutomaticTheme();prefs.edit().putBoolean("autoDayNight",autoDayNight).apply();}
        else if(helpButton.contains(e.getX(),e.getY()))showSettings();
        else if(aboutButton.contains(e.getX(),e.getY()))showAbout();
        invalidate();return true;
    }
    private void showHelp(){String en="Mount the phone firmly in landscape orientation, then park on level ground and tap CALIBRATE.\n\nROLL shows side-to-side tilt using the front view. PITCH shows nose-up or nose-down tilt using the side view.\n\nTHEME changes only colors. VEHICLE changes only the vehicle. AUTO chooses day/night from GPS. MUTE disables alarms.\n\nGPS speed and position require outdoor location reception. Never operate the app while driving.";String ru="Надёжно закрепите телефон горизонтально. Поставьте автомобиль на ровную поверхность и нажмите КАЛИБРОВКА.\n\nКРЕН показывает наклон из стороны в сторону и использует вид спереди. ТАНГАЖ показывает наклон вперёд или назад и использует вид сбоку.\n\nТЕМА меняет только цвета, ТРАНСПОРТ — только машину. АВТО выбирает день или ночь по GPS. ВЫКЛ. ЗВУК отключает сигнализацию.\n\nНе управляйте приложением во время движения.";String he="חבר את הטלפון היטב במצב אופקי. החנה את הרכב על משטח ישר ולחץ כיול.\n\nגלגול מציג הטיה מצד לצד עם מבט קדמי. עלרוד מציג הטיה קדימה או אחורה עם מבט צד.\n\nערכת נושא משנה רק צבעים, ורכב משנה רק את הרכב. מצב אוטומטי בוחר יום או לילה לפי GPS. השתק מכבה התראות.\n\nאין להפעיל את היישום בזמן נהיגה.";new AlertDialog.Builder(getContext()).setTitle(tr("How to use","Как пользоваться","כיצד להשתמש")).setMessage(tr(en,ru,he)).setPositiveButton(tr("Got it","Понятно","הבנתי"),null).show();}
    private void showSettings(){String[] options={tr("Swap Roll / Pitch axes","Поменять оси Крен / Тангаж","החלף צירי גלגול / עלרוד"),tr("Invert Roll direction","Инвертировать направление крена","הפוך כיוון גלגול"),tr("Invert Pitch direction","Инвертировать направление тангажа","הפוך כיוון עלרוד")};boolean[] selected={swapAxes,invertRoll,invertPitch};new AlertDialog.Builder(getContext()).setTitle(tr("Sensor settings","Настройки датчиков","הגדרות חיישנים")).setMultiChoiceItems(options,selected,(d,which,checked)->{if(which==0)swapAxes=checked;else if(which==1)invertRoll=checked;else invertPitch=checked;zeroRoll=zeroPitch=0;prefs.edit().putBoolean("swapAxes",swapAxes).putBoolean("invertRoll",invertRoll).putBoolean("invertPitch",invertPitch).putFloat("zeroRoll",0).putFloat("zeroPitch",0).apply();invalidate();}).setNeutralButton(tr("LANGUAGE","ЯЗЫК","שפה"),(d,w)->showLanguage()).setNegativeButton(tr("HELP","ПОМОЩЬ","עזרה"),(d,w)->showHelp()).setPositiveButton(tr("Done","Готово","סיום"),null).show();}
    private void showLanguage(){String[] names={"English","Русский","עברית"};new AlertDialog.Builder(getContext()).setTitle(tr("Language","Язык","שפה")).setSingleChoiceItems(names,language,(d,which)->{language=which;prefs.edit().putInt("language",language).apply();d.dismiss();invalidate();}).setNegativeButton(tr("Close","Закрыть","סגור"),null).show();}
    private void showAbout(){String en="Version 1.1.1\nDeveloped by Chysti Alex\n© 2026 Chysti Alex\n\nGPS and sensor data are processed locally on your device and are not collected or transmitted.\n\nSupport is voluntary and does not unlock features or digital benefits.\n\nThis app is an informational aid, not a certified safety instrument.";String ru="Версия 1.1.1\nРазработчик: Chysti Alex\n© 2026 Chysti Alex\n\nДанные GPS и датчиков обрабатываются только на устройстве, не собираются и не передаются.\n\nПоддержка разработчика добровольна и не открывает функции или цифровые преимущества.\n\nПриложение является информационным помощником, а не сертифицированным прибором безопасности.";String he="גרסה 1.1.1\nפותח על ידי Chysti Alex\n© 2026 Chysti Alex\n\nנתוני GPS וחיישנים מעובדים במכשיר בלבד ואינם נאספים או מועברים.\n\nהתמיכה במפתח היא מרצון ואינה פותחת תכונות או הטבות דיגיטליות.\n\nהיישום הוא כלי מידע ואינו מכשיר בטיחות מוסמך.";new AlertDialog.Builder(getContext()).setTitle(tr("Car Inclinometer — About","Car Inclinometer — О программе","Car Inclinometer — אודות")).setMessage(tr(en,ru,he)).setNeutralButton(tr("Support the developer","Поддержать разработчика","תמיכה במפתח"),(d,w)->getContext().startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://paypal.me/Chysti75")))).setPositiveButton(tr("Close","Закрыть","סגור"),null).show();}
    private void applyAutomaticTheme(){
        if(!autoDayNight||!hasGps)return;lastSolarCheck=System.currentTimeMillis();
        Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));int day=utc.get(Calendar.DAY_OF_YEAR);double hour=utc.get(Calendar.HOUR_OF_DAY)+utc.get(Calendar.MINUTE)/60.0;
        double gamma=2*Math.PI/365.0*(day-1+(hour-12)/24.0);
        double decl=.006918-.399912*Math.cos(gamma)+.070257*Math.sin(gamma)-.006758*Math.cos(2*gamma)+.000907*Math.sin(2*gamma)-.002697*Math.cos(3*gamma)+.00148*Math.sin(3*gamma);
        double eq=229.18*(.000075+.001868*Math.cos(gamma)-.032077*Math.sin(gamma)-.014615*Math.cos(2*gamma)-.040849*Math.sin(2*gamma));
        double minutes=utc.get(Calendar.HOUR_OF_DAY)*60+utc.get(Calendar.MINUTE)+utc.get(Calendar.SECOND)/60.0;double solar=(minutes+eq+4*longitude)%1440;if(solar<0)solar+=1440;
        double ha=Math.toRadians(solar/4.0-180);double lat=Math.toRadians(latitude);double cosZenith=Math.sin(lat)*Math.sin(decl)+Math.cos(lat)*Math.cos(decl)*Math.cos(ha);
        daylight=cosZenith>Math.cos(Math.toRadians(90.833));theme=daylight?2:0;prefs.edit().putInt("theme",theme).apply();
    }
    private static float normalize(float v){while(v>180)v-=360;while(v<-180)v+=360;return v;}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private String tr(String en,String ru,String he){return language==1?ru:language==2?he:en;}
    private String themeName(){String[][] n={{"NEON","AMBER","ICE"},{"НЕОН","ЯНТАРЬ","ЛЁД"},{"ניאון","ענבר","קרח"}};return n[language][theme];}
    private String vehicleName(){String[][] n={{"SEDAN","HUMMER","TANK","BUGGY"},{"СЕДАН","ХАММЕР","ТАНК","БАГГИ"},{"סדאן","האמר","טנק","באגי"}};return n[language][vehicle];}
    private String soundName(){String[][] n={{"BEEP","SIREN","ALERT","DOUBLE"},{"СИГНАЛ","СИРЕНА","ТРЕВОГА","ДВОЙНОЙ"},{"צפצוף","סירנה","התראה","כפול"}};return n[language][alarmSound];}
    private static int withAlpha(int color,int alpha){return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color));}
    private static Path circlePath(float x,float y,float r){Path q=new Path();q.addCircle(x,y,r,Path.Direction.CW);return q;}
}
