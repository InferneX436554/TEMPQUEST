package com.tempquest.monitor;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class StopAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){context.stopService(new Intent(context,AlarmPlaybackService.class));}
}
