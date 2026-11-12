package com.tempquest.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class AlarmPlaybackService extends Service {
    public static final String CHANNEL="tempquest_alarm";
    public static final String ONGOING_CHANNEL="tempquest_alarm_active_v2";
    public static final String ACTION_START="com.tempquest.monitor.ALARM_START";
    public static final String ACTION_STOP="com.tempquest.monitor.ALARM_STOP";
    private static final int NOTIFICATION_ID=4201;
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private double lastTemperature=Double.NaN;
    private int lastServerNumber=1;
    private String lastServerName="SERVIDOR 1";
    @Override public void onCreate(){super.onCreate();vibrator=getSystemService(Vibrator.class);PowerManager pm=getSystemService(PowerManager.class);wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TempQuest:Alarm");ensureChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){stopAlarm();stopSelf();return START_NOT_STICKY;}
        String detail=intent==null?"Temperatura alta":intent.getStringExtra("detail");
        lastTemperature=intent==null?Double.NaN:intent.getDoubleExtra("temperature",Double.NaN);
        lastServerNumber=intent==null?1:intent.getIntExtra("server_number",1);
        lastServerName=intent==null?"SERVIDOR 1":intent.getStringExtra("server_name");if(lastServerName==null||lastServerName.trim().isEmpty())lastServerName="SERVIDOR "+lastServerNumber;
        startForeground(NOTIFICATION_ID,buildNotification(detail));playAlarm();return START_STICKY;
    }
    private void ensureChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=getSystemService(NotificationManager.class);if(nm.getNotificationChannel(ONGOING_CHANNEL)==null){NotificationChannel ch=new NotificationChannel(ONGOING_CHANNEL,"Alarma activa",NotificationManager.IMPORTANCE_HIGH);ch.enableVibration(true);nm.createNotificationChannel(ch);}}}
    private Notification buildNotification(String detail){
        Intent open=new Intent(this,AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("detail",detail).putExtra("temperature",lastTemperature).putExtra("server_number",lastServerNumber).putExtra("server_name",lastServerName);PendingIntent full=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,StopAlarmReceiver.class);PendingIntent stopPi=PendingIntent.getBroadcast(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ONGOING_CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("¡Servidor en peligro por temperatura!").setContentText(detail==null?"El servidor está sufriendo altas temperaturas.":detail).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_MAX).setOngoing(true).setAutoCancel(false).setContentIntent(full).setFullScreenIntent(full,true).addAction(android.R.drawable.ic_media_pause,"DETENER ALARMA",stopPi).build();
    }
    private void playAlarm(){
        if(wakeLock!=null&&!wakeLock.isHeld())wakeLock.acquire();
        startAlarmVibration();
        if(player!=null&&player.isPlaying())return;
        try{player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());player.setDataSource(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));player.setLooping(true);player.prepare();player.start();}
        catch(Exception e){Log.e("TempQuest","No se pudo reproducir el tono de alarma",e);}
    }
    private void startAlarmVibration(){
        if(vibrator==null||!vibrator.hasVibrator()){Log.w("TempQuest","Este teléfono no informa un vibrador disponible");return;}
        try{
            long[] pattern={0,650,250,650,600};
            VibrationEffect effect=VibrationEffect.createWaveform(pattern,0);
            if(Build.VERSION.SDK_INT>=33){
                android.os.VibrationAttributes attributes=new android.os.VibrationAttributes.Builder().setUsage(android.os.VibrationAttributes.USAGE_ALARM).build();
                vibrator.vibrate(effect,attributes);
            }else vibrator.vibrate(effect);
        }catch(Exception e){Log.e("TempQuest","No se pudo iniciar la vibración de alarma",e);}
    }
    private void stopAlarm(){try{if(player!=null){if(player.isPlaying())player.stop();player.release();player=null;}}catch(Exception ignored){}if(vibrator!=null)vibrator.cancel();if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);}
    @Override public void onDestroy(){stopAlarm();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}



