package com.tempquest.monitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen high contrast warning shown while a temperature alarm is active. */
public class AlarmActivity extends Activity {
    private static final int RED = Color.rgb(151, 12, 23);
    private static final int MAX_TEMP = 80;
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER); return v;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(75, 4, 10));
        getWindow().setNavigationBarColor(Color.rgb(52, 3, 8));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER);
        page.setPadding(dp(24), dp(20), dp(24), dp(24));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(67, 4, 9), RED, Color.rgb(82, 5, 12)});
        page.setBackground(background);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            int top=0,bottom=0;
            if (Build.VERSION.SDK_INT >= 30) { top=insets.getInsets(WindowInsets.Type.statusBars()).top; bottom=insets.getInsets(WindowInsets.Type.navigationBars()).bottom; }
            else { top=insets.getSystemWindowInsetTop(); bottom=insets.getSystemWindowInsetBottom(); }
            v.setPadding(dp(24),dp(16)+top,dp(24),dp(20)+bottom); return insets;
        });

        TextView badge = text("●  ALERTA ACTIVA", 13, Color.WHITE, true);
        GradientDrawable badgeBg = new GradientDrawable(); badgeBg.setColor(0x337A0000); badgeBg.setCornerRadius(dp(30));
        badgeBg.setStroke(dp(1), 0x99FFFFFF); badge.setBackground(badgeBg); badge.setPadding(dp(18),dp(9),dp(18),dp(9));
        LinearLayout.LayoutParams badgeP=new LinearLayout.LayoutParams(-2,-2); badgeP.bottomMargin=dp(28); page.addView(badge,badgeP);

        WarningIcon warning = new WarningIcon();
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(116),dp(104)); iconP.bottomMargin=dp(14); page.addView(warning,iconP);

        TextView title = text("TEMPERATURA CRÍTICA", 23, Color.WHITE, true);
        page.addView(title,new LinearLayout.LayoutParams(-1,-2));

        int serverNumber=getIntent().getIntExtra("server_number",1);
        String serverName=getIntent().getStringExtra("server_name");if(serverName==null||serverName.trim().isEmpty())serverName="SERVIDOR "+serverNumber;TextView server=text(serverName.toUpperCase(java.util.Locale.ROOT),18,Color.WHITE,true);
        GradientDrawable serverBg=new GradientDrawable();serverBg.setColor(0x33700000);serverBg.setCornerRadius(dp(12));serverBg.setStroke(dp(2),0xCCFFFFFF);server.setBackground(serverBg);server.setPadding(dp(18),dp(9),dp(18),dp(9));
        LinearLayout.LayoutParams serverP=new LinearLayout.LayoutParams(-2,-2);serverP.topMargin=dp(12);page.addView(server,serverP);
        double temperature=getIntent().getDoubleExtra("temperature",Double.NaN);
        TextView reading=text(Double.isNaN(temperature)?"— °C":String.format(java.util.Locale.getDefault(),"%.0f °C",temperature),48,Color.WHITE,true);
        LinearLayout.LayoutParams readingP=new LinearLayout.LayoutParams(-1,-2); readingP.topMargin=dp(5); page.addView(reading,readingP);

        TextView maximum=text("Máximo permitido: 80 °C",15,Color.rgb(255,221,221),false);
        LinearLayout.LayoutParams maxP=new LinearLayout.LayoutParams(-1,-2); maxP.topMargin=dp(2); page.addView(maximum,maxP);

        TextView message=text("El servidor está sufriendo altas temperaturas y está en peligro.",18,Color.WHITE,true);
        message.setLineSpacing(dp(4),1f);
        GradientDrawable messageBg=new GradientDrawable(); messageBg.setColor(0x33700000); messageBg.setCornerRadius(dp(12)); messageBg.setStroke(dp(1),0x55FFFFFF);
        message.setBackground(messageBg); message.setPadding(dp(16),dp(18),dp(16),dp(18));
        LinearLayout.LayoutParams messageP=new LinearLayout.LayoutParams(-1,-2); messageP.topMargin=dp(16); page.addView(message,messageP);

        TextView advice=text("Revisá la refrigeración inmediatamente.",14,Color.rgb(255,226,226),false);
        LinearLayout.LayoutParams adviceP=new LinearLayout.LayoutParams(-1,-2); adviceP.topMargin=dp(16); page.addView(advice,adviceP);

        Button stop=new Button(this); stop.setText("■   DETENER ALARMA"); stop.setTextSize(17);
        stop.setTypeface(Typeface.DEFAULT,Typeface.BOLD); stop.setTextColor(Color.rgb(111,7,15)); stop.setAllCaps(false);
        stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams stopP=new LinearLayout.LayoutParams(-1,dp(62)); stopP.topMargin=dp(34); page.addView(stop,stopP);
        stop.setOnClickListener(v->{stopService(new Intent(this,AlarmPlaybackService.class));finish();});

        TextView footer=text("SONIDO Y VIBRACIÓN ACTIVOS",11,0xCCFFFFFF,true);
        LinearLayout.LayoutParams footerP=new LinearLayout.LayoutParams(-1,-2); footerP.topMargin=dp(22); page.addView(footer,footerP);
        setContentView(page);
    }

    private final class WarningIcon extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        WarningIcon(){super(AlarmActivity.this);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas); float w=getWidth(),h=getHeight();
            Path triangle=new Path(); triangle.moveTo(w*.5f,h*.08f); triangle.lineTo(w*.94f,h*.88f); triangle.lineTo(w*.06f,h*.88f); triangle.close();
            paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setStrokeJoin(Paint.Join.ROUND); canvas.drawPath(triangle,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(6)); paint.setStrokeCap(Paint.Cap.ROUND); canvas.drawLine(w*.5f,h*.35f,w*.5f,h*.58f,paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(w*.5f,h*.72f,dp(3.5f),paint);
        }
    }
}
