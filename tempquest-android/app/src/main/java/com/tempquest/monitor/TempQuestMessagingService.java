package com.tempquest.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TempQuestMessagingService extends FirebaseMessagingService {
    private static final String CHANGE_CHANNEL = "tempquest_database_changes";
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    @Override public void onMessageReceived(RemoteMessage message) {
        String type=message.getData().get("type");
        if("high_temperature".equals(type)) {
            String value=message.getData().getOrDefault("temperature", "");
            String device=message.getData().getOrDefault("device", "Servidor");
            int serverNumber=1; try { serverNumber=Integer.parseInt(message.getData().getOrDefault("server_number","1")); } catch(NumberFormatException ignored) { }
            String serverLabel=MainActivity.registeredServerName(this,String.valueOf(serverNumber));
            String detail=value.isEmpty()?serverLabel+" · Temperatura alta":serverLabel+": "+value+" °C";
            double temperature=Double.NaN; try { if(!value.isEmpty()) temperature=Double.parseDouble(value); } catch(NumberFormatException ignored) { }
            Intent alarm=new Intent(this,AlarmPlaybackService.class).setAction(AlarmPlaybackService.ACTION_START).putExtra("detail",detail).putExtra("temperature",temperature).putExtra("server_number",serverNumber).putExtra("server_name",serverLabel).putExtra("alert_id",message.getData().getOrDefault("alert_id",""));
            try { if(Build.VERSION.SDK_INT>=26)startForegroundService(alarm);else startService(alarm); } catch(Exception e){ Log.e("TempQuest","No se pudo iniciar la alarma",e); }
            return;
        }
        String title=message.getData().getOrDefault("title", "Cambio detectado en NEXUS NOC");
        String body=message.getData().getOrDefault("body", "Se modificaron datos en la base de datos.");
        showChangeNotification(title,body);
    }

    private void showChangeNotification(String title,String body) {
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26 && manager.getNotificationChannel(CHANGE_CHANNEL)==null) {
            NotificationChannel channel=new NotificationChannel(CHANGE_CHANNEL,"Cambios de la base de datos",NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Avisos de cambios en dispositivos y mediciones de NEXUS NOC");
            channel.enableVibration(true); manager.createNotificationChannel(channel);
        }
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this, (int)(System.currentTimeMillis() & 0x7fffffff),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANGE_CHANNEL):new Notification.Builder(this);
        Notification notification=builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pending).setCategory(Notification.CATEGORY_STATUS).build();
        manager.notify((int)(System.currentTimeMillis() & 0x7fffffff),notification);
    }

    @Override public void onNewToken(String token){io.execute(()->register(token));}
    private void register(String token){
        if(!BuildConfig.FCM_CONFIGURED)return;
        HttpURLConnection c=null;
        try{c=(HttpURLConnection)new URL(MainActivity.serverUrl(this)+"/api/register-device").openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(new JSONObject().put("token",token).toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();if(code<200||code>=300)Log.w("TempQuest","Registro FCM rechazado: HTTP "+code);}
        catch(Exception e){Log.w("TempQuest","No se pudo registrar el teléfono para alertas",e);}
        finally{if(c!=null)c.disconnect();}
    }
}
