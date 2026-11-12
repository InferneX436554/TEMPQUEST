package com.tempquest.monitor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String PREFS = "tempquest";
    static final String PREF_URL = "server_url";
    static String serverUrl(Context c) { return c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_URL, "http://10.0.2.2/nexus_mysql").replaceAll("/+$", ""); }
    static String registeredServerName(Context context,String deviceId){try{JSONArray saved=new JSONArray(context.getSharedPreferences(PREFS,MODE_PRIVATE).getString("registered_servers","[]"));for(int i=0;i<saved.length();i++){JSONObject item=saved.optJSONObject(i);if(item!=null&&deviceId.equals(item.optString("id")))return item.optString("name","SERVIDOR "+deviceId);}}catch(Exception ignored){}return "SERVIDOR "+deviceId;}
    private int bg,panel,line,text,muted,red,green,amber,headerBg,cardStroke,neutralButton,neutralButtonText;
    private boolean lightTheme;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout root, content, nav, serverDropdownList; private TextView connection, serverPickerTitle, selectedServerBadge; private ChevronToggleView serverPickerArrow; private ScrollView serverDropdownScroll; private PopupWindow serverDropdown; private View serverPicker; private GaugeView mainGauge; private boolean serverDropdownOpen=false; private final java.util.Set<String> knownDeviceIds=new java.util.HashSet<>(); private final List<GaugeView> serverGauges=new ArrayList<>(); private final List<TextView> serverTitles=new ArrayList<>(), serverLimits=new ArrayList<>(), serverBadges=new ArrayList<>(), serverTabLabels=new ArrayList<>(); private HistoryChartView chart;
    private static final double GAUGE_MAX_C=100.0;
    private String activePage="home", selectedDeviceId="1"; private double temperature=Double.NaN, threshold=80, humidity=Double.NaN, pressure=Double.NaN; private boolean databaseConnected=false, provisionalReadings=true,hasInitializedServerSelection=false; private int selectedServerIndex=0,pageWidth=0;
    private List<ServerConfig> registeredServers=defaultServerConfigs(); private List<ServerReading> serverReadings=provisionalReadings(); private JSONArray alerts=new JSONArray(), localTestAlerts=new JSONArray(), serverAlerts=new JSONArray(); private final List<Double> chartValues=new ArrayList<>(); private final Map<String,List<Double>> chartHistories=new HashMap<>();
    private static class ServerConfig { final String id,name; final int number; ServerConfig(String id,String name,int number){this.id=id;this.name=name;this.number=number;} }
    private static class ServerReading { final int number; final String id,displayName; final double temperature,humidity,pressure; final boolean provisional; ServerReading(int n,String i,double t,double h,double p,boolean demo){this(n,i,t,h,p,demo,"SERVIDOR "+n);} ServerReading(int n,String i,double t,double h,double p,boolean demo,String display){number=n;id=i;temperature=t;humidity=h;pressure=p;provisional=demo;displayName=display;} String name(){return displayName;} }
    private List<ServerConfig> defaultServerConfigs(){List<ServerConfig> values=new ArrayList<>();values.add(new ServerConfig("1","SERVIDOR 1",1));values.add(new ServerConfig("2","SERVIDOR 2",2));return values;}
    private void loadServerConfigs(){try{JSONArray saved=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString("registered_servers","[]"));if(saved.length()>0){List<ServerConfig> loaded=new ArrayList<>();for(int i=0;i<saved.length();i++){JSONObject item=saved.optJSONObject(i);if(item==null)continue;String id=item.optString("id","").trim(),name=item.optString("name","").trim();int number=item.optInt("number",i+1);if(!id.isEmpty()&&!name.isEmpty())loaded.add(new ServerConfig(id,name,number));}if(!loaded.isEmpty()){registeredServers=loaded;return;}}}catch(Exception ignored){}registeredServers=defaultServerConfigs();saveServerConfigs();}
    private void saveServerConfigs(){JSONArray saved=new JSONArray();for(ServerConfig item:registeredServers)try{saved.put(new JSONObject().put("id",item.id).put("name",item.name).put("number",item.number));}catch(Exception ignored){}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("registered_servers",saved.toString()).apply();}
    private int nextServerNumber(){int next=1;for(ServerConfig item:registeredServers)next=Math.max(next,item.number+1);return next;}
    private List<ServerReading> provisionalReadings(){List<ServerReading> values=new ArrayList<>();for(ServerConfig config:registeredServers){double demo=Double.NaN;boolean provisional=false;if(config.id.equals("1")){demo=84;provisional=true;}else if(config.id.equals("2")){demo=65;provisional=true;}values.add(new ServerReading(config.number,config.id,demo,Double.NaN,Double.NaN,provisional,config.name));}values.sort((a,b)->{if(Double.isFinite(a.temperature)&&Double.isFinite(b.temperature))return Double.compare(b.temperature,a.temperature);if(Double.isFinite(a.temperature))return -1;if(Double.isFinite(b.temperature))return 1;return Integer.compare(a.number,b.number);});return values;}
    private final Runnable poller = new Runnable(){ @Override public void run(){ refresh(); handler.postDelayed(this,5000); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); loadServerConfigs(); applyThemeColors(); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);getWindow().getDecorView().setSystemUiVisibility(lightTheme?(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0); createLocalChannels(); requestNotificationPermission(); requestFullScreenAlarmAccess(); buildShell();
        if (BuildConfig.FCM_CONFIGURED) registerFcmToken(); refresh(); handler.postDelayed(poller,5000);
    }
    @Override protected void onDestroy(){ handler.removeCallbacks(poller); io.shutdownNow(); super.onDestroy(); }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private double finiteNumber(JSONObject object,String key){if(object==null||object.isNull(key))return Double.NaN;Object raw=object.opt(key);if(!(raw instanceof Number)&&!(raw instanceof String))return Double.NaN;try{double value=Double.parseDouble(String.valueOf(raw).trim());return Double.isFinite(value)?value:Double.NaN;}catch(Exception ignored){return Double.NaN;}}
    private String formatTemperature(double value){if(!Double.isFinite(value))return "Sin datos";return String.format(java.util.Locale.getDefault(),Math.abs(value-Math.rint(value))<0.05?"%.0f °C":"%.1f °C",value);}
    private void applyThemeColors(){lightTheme=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("light_theme",false);red=Color.rgb(232,75,81);green=Color.rgb(70,205,137);amber=Color.rgb(239,174,55);if(lightTheme){bg=Color.rgb(244,247,249);panel=Color.WHITE;line=Color.rgb(205,215,222);text=Color.rgb(25,35,45);muted=Color.rgb(80,94,106);headerBg=Color.rgb(250,252,253);cardStroke=Color.rgb(205,215,222);neutralButton=Color.rgb(231,236,239);neutralButtonText=Color.rgb(40,50,59);}else{bg=Color.rgb(7,8,9);panel=bg;line=Color.rgb(54,60,63);text=Color.rgb(242,244,243);muted=Color.rgb(157,165,167);headerBg=bg;cardStroke=line;neutralButton=bg;neutralButtonText=Color.rgb(220,224,223);}}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void buildShell(){
        root=column();root.setBackgroundColor(bg);setContentView(root);
        // Android 15 enforces edge-to-edge for targetSdk 35. Keep app content below
        // the status bar and above gesture navigation on both new and older Android.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top=0,bottom=0;
            if(Build.VERSION.SDK_INT>=30){WindowInsets wi=insets;top=wi.getInsets(WindowInsets.Type.statusBars()).top;bottom=wi.getInsets(WindowInsets.Type.navigationBars()).bottom;}
            else {top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(0,top,0,bottom);return insets;
        });
        LinearLayout header=row();header.setPadding(dp(14),dp(4),dp(10),dp(4));header.setBackgroundColor(headerBg);
        LinearLayout brandGroup=row();brandGroup.addView(new BrandIconView(),new LinearLayout.LayoutParams(dp(28),dp(28)));TextView brand=label("TEMPQUEST",19,text,true);LinearLayout.LayoutParams brandTextP=new LinearLayout.LayoutParams(-2,-2);brandTextP.leftMargin=dp(7);brandGroup.addView(brand,brandTextP);header.addView(brandGroup,new LinearLayout.LayoutParams(0,dp(44),1));
        ThemeToggleView themeToggle=new ThemeToggleView();themeToggle.setBackground(round(panel,dp(18),line));themeToggle.setOnClickListener(v->{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("light_theme",!lightTheme).apply();recreate();});themeToggle.setContentDescription(lightTheme?"Cambiar a tema oscuro":"Cambiar a tema claro");LinearLayout.LayoutParams themeP=new LinearLayout.LayoutParams(dp(42),dp(38));themeP.rightMargin=dp(7);header.addView(themeToggle,themeP);
        connection=label("● SIN CONEXIÓN",10,red,true);connection.setPadding(dp(9),dp(7),dp(9),dp(7));connection.setBackground(round(lightTheme?Color.rgb(255,235,237):Color.rgb(45,18,20),dp(20),lightTheme?Color.rgb(245,185,190):Color.rgb(102,37,42)));connection.setOnClickListener(v->editServer());connection.setContentDescription("Estado de conexión. Toca para configurar el servidor.");header.addView(connection);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(dp(9),dp(7),dp(9),dp(7));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();nav.setBackgroundColor(headerBg);nav.setPadding(dp(12),dp(4),dp(12),dp(4));nav.setElevation(dp(8));root.addView(nav,new LinearLayout.LayoutParams(-1,dp(70)));showPage("home");
    }
    private Button smallButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(neutralButtonText);b.setAllCaps(false);b.setMinHeight(dp(44));b.setBackground(round(neutralButton,dp(12),line));return b;}
    private TextView heading(String s){TextView t=label(s,23,text,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(t,p);return t;}
    private TextView eyebrow(String s){TextView t=label(s,13,muted,true);t.setLetterSpacing(.08f);return t;}
    private void showPage(String page){if(serverDropdown!=null&&serverDropdown.isShowing())serverDropdown.dismiss();activePage=page;content.removeAllViews();nav.removeAllViews();
        nav.addView(navItem("","INICIO",page.equals("home"),()->showPage("home")),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("","ALERTAS",page.equals("alerts"),()->{showPage("alerts");loadAlerts();}),new LinearLayout.LayoutParams(0,-1,1));
        if(page.equals("home"))showHome();else showAlerts();
    }
    private View navItem(String icon,String title,boolean selected,Runnable action){LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setBackgroundColor(headerBg);boolean isHome=title.equals("INICIO");item.addView(new NavIconView(isHome,selected?red:muted),new LinearLayout.LayoutParams(dp(30),dp(29)));TextView name=label(title,16,selected?red:muted,true);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));name.setGravity(Gravity.CENTER);item.addView(name,new LinearLayout.LayoutParams(-1,dp(23)));View underline=new View(this);underline.setBackgroundColor(selected?red:Color.TRANSPARENT);LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(dp(80),dp(3));up.topMargin=dp(2);item.addView(underline,up);item.setOnClickListener(v->action.run());return item;}
    private void showHome(){
        serverDropdownOpen=false;
        content.addView(eyebrow("MONITOREO EN TIEMPO REAL"));heading("Temperatura del servidor");
        LinearLayout gaugeCard=column();gaugeCard.setGravity(Gravity.CENTER);gaugeCard.setPadding(dp(12),dp(9),dp(12),dp(9));gaugeCard.setBackground(round(panel,dp(16),cardStroke));LinearLayout cardHeading=row();TextView gaugeLabel=label("TEMPERATURA DEL SERVIDOR",13,muted,true);cardHeading.addView(gaugeLabel,new LinearLayout.LayoutParams(0,-2,1));selectedServerBadge=label("MÁS CALIENTE",10,red,true);selectedServerBadge.setGravity(Gravity.CENTER);selectedServerBadge.setPadding(dp(10),dp(5),dp(10),dp(5));selectedServerBadge.setBackground(round(lightTheme?Color.rgb(255,239,239):Color.rgb(43,19,21),dp(18),red));cardHeading.addView(selectedServerBadge);gaugeCard.addView(cardHeading);mainGauge=new GaugeView(this);gaugeCard.addView(mainGauge,new LinearLayout.LayoutParams(-1,-2));TextView limit=label("Máximo permitido: 80 °C",14,muted,true);limit.setGravity(Gravity.CENTER);gaugeCard.addView(limit);content.addView(gaugeCard,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout picker=row();picker.setPadding(dp(13),dp(9),dp(10),dp(9));picker.setBackground(round(panel,dp(13),line));serverPickerTitle=label("SERVIDOR 1",16,text,true);picker.addView(serverPickerTitle,new LinearLayout.LayoutParams(0,-2,1));serverPickerArrow=new ChevronToggleView();picker.addView(serverPickerArrow,new LinearLayout.LayoutParams(dp(34),dp(34)));picker.setContentDescription("Mostrar los servidores activos");picker.setOnClickListener(v->toggleServerDropdown());serverPicker=picker;LinearLayout.LayoutParams pickerP=new LinearLayout.LayoutParams(-1,-2);pickerP.topMargin=dp(10);content.addView(picker,pickerP);
        serverDropdownScroll=new ScrollView(this);serverDropdownScroll.setFillViewport(true);serverDropdownScroll.setVerticalScrollBarEnabled(false);serverDropdownList=column();serverDropdownList.setPadding(dp(6),dp(6),dp(6),dp(6));serverDropdownList.setBackground(round(panel,dp(14),line));serverDropdownScroll.addView(serverDropdownList);serverDropdown=new PopupWindow(serverDropdownScroll,-1,dp(210),true);serverDropdown.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));serverDropdown.setOutsideTouchable(true);serverDropdown.setElevation(dp(8));serverDropdown.setOnDismissListener(()->{serverDropdownOpen=false;if(serverPickerArrow!=null)serverPickerArrow.setExpanded(false);});
        LinearLayout stats=row();stats.addView(statBox("HUMEDAD", "Sin datos",true),new LinearLayout.LayoutParams(0,dp(68),1));stats.addView(space(dp(8)),new LinearLayout.LayoutParams(dp(8),1));stats.addView(statBox("PRESIÓN","Sin datos",false),new LinearLayout.LayoutParams(0,dp(68),1));LinearLayout.LayoutParams statsP=new LinearLayout.LayoutParams(-1,-2);statsP.bottomMargin=dp(2);content.addView(stats,statsP);
        chart=new HistoryChartView(this);LinearLayout chartCard=column();chartCard.setPadding(dp(12),dp(11),dp(12),dp(7));chartCard.setBackground(round(panel,dp(15),line));TextView ch=label("TENDENCIA DE TEMPERATURA",15,text,true);chartCard.addView(ch);chartCard.addView(chart,new LinearLayout.LayoutParams(-1,dp(168)));LinearLayout.LayoutParams chp=new LinearLayout.LayoutParams(-1,-2);chp.topMargin=dp(10);content.addView(chartCard,chp);
        Button test=smallButton("PROBAR ALARMA · 84 °C");test.setTextSize(14);test.setTextColor(Color.WHITE);test.setBackground(round(red,dp(13),Color.rgb(204,59,65)));test.setOnClickListener(v->{ServerReading selected=selectedReading();saveTestAlert(84.0,selected.name());startAlarm("PRUEBA · "+selected.name()+" · Temperatura simulada: 84 °C",84.0,selected.number,selected.name());});LinearLayout.LayoutParams testP=new LinearLayout.LayoutParams(-1,dp(48));testP.topMargin=dp(12);content.addView(test,testP);
        renderServerDropdown();updateServerViews();
    }
    private void toggleServerDropdown(){if(serverDropdown==null||serverPicker==null)return;if(serverDropdown.isShowing()){serverDropdown.dismiss();return;}renderServerDropdown();serverDropdown.setWidth(serverPicker.getWidth());serverDropdown.showAsDropDown(serverPicker,0,dp(4));serverDropdownOpen=true;serverPickerArrow.setExpanded(true);}
    private void renderServerDropdown(){if(serverDropdownList==null)return;serverDropdownList.removeAllViews();for(int i=0;i<serverReadings.size();i++){final int index=i;ServerReading reading=serverReadings.get(i);TextView item=label(reading.name()+"  ·  "+formatTemperature(reading.temperature),15,i==selectedServerIndex?red:text,true);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(13),dp(11),dp(13),dp(11));item.setBackground(round(i==selectedServerIndex?(lightTheme?Color.rgb(255,239,239):Color.rgb(43,19,21)):panel,dp(12),i==selectedServerIndex?red:line));item.setOnClickListener(v->{setSelectedServer(index);if(serverDropdown!=null)serverDropdown.dismiss();});LinearLayout.LayoutParams itemP=new LinearLayout.LayoutParams(-1,dp(46));itemP.bottomMargin=dp(5);serverDropdownList.addView(item,itemP);}TextView add=label("＋  AGREGAR SERVIDOR",15,red,true);add.setGravity(Gravity.CENTER);add.setPadding(dp(8),dp(10),dp(8),dp(10));add.setBackground(round(lightTheme?Color.rgb(255,239,239):Color.rgb(43,19,21),dp(12),red));add.setOnClickListener(v->{if(serverDropdown!=null)serverDropdown.dismiss();showAddServerDialog();});serverDropdownList.addView(add,new LinearLayout.LayoutParams(-1,dp(46)));int height=Math.min(dp(280),dp(12)+serverReadings.size()*dp(51)+dp(46));if(serverDropdown!=null){serverDropdown.setHeight(height);if(serverPicker!=null&&serverPicker.getWidth()>0)serverDropdown.setWidth(serverPicker.getWidth());}}
    private void showAddServerDialog(){int next=nextServerNumber();LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(4));TextView hint=label("Ingresá el ID de un dispositivo que ya exista en MySQL. Se guardará solo en este teléfono.",14,muted,false);form.addView(hint);EditText id=new EditText(this);id.setSingleLine(true);id.setInputType(InputType.TYPE_CLASS_NUMBER);id.setHint("ID del dispositivo en MySQL");id.setText(String.valueOf(next));LinearLayout.LayoutParams idP=new LinearLayout.LayoutParams(-1,-2);idP.topMargin=dp(10);form.addView(id,idP);EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Nombre (opcional)");name.setText("Servidor "+next);LinearLayout.LayoutParams nameP=new LinearLayout.LayoutParams(-1,-2);nameP.topMargin=dp(6);form.addView(name,nameP);new AlertDialog.Builder(this).setTitle("Agregar servidor").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Agregar",(dialog,which)->{String deviceId=id.getText().toString().trim(),display=name.getText().toString().trim();if(deviceId.isEmpty()){Toast.makeText(this,"Ingresá el ID del dispositivo de MySQL.",Toast.LENGTH_LONG).show();return;}for(ServerConfig item:registeredServers)if(item.id.equals(deviceId)){Toast.makeText(this,"Ese dispositivo ya está agregado.",Toast.LENGTH_LONG).show();return;}if(databaseConnected&&!knownDeviceIds.contains(deviceId)){Toast.makeText(this,"No se encontró ese ID entre los dispositivos activos de MySQL.",Toast.LENGTH_LONG).show();return;}if(display.isEmpty())display="Servidor "+next;registeredServers.add(new ServerConfig(deviceId,display,next));saveServerConfigs();serverReadings.add(new ServerReading(next,deviceId,Double.NaN,Double.NaN,Double.NaN,false,display));serverReadings.sort((a,b)->{if(Double.isFinite(a.temperature)&&Double.isFinite(b.temperature))return Double.compare(b.temperature,a.temperature);if(Double.isFinite(a.temperature))return -1;if(Double.isFinite(b.temperature))return 1;return Integer.compare(a.number,b.number);});selectedServerIndex=0;selectedDeviceId=serverReadings.get(0).id;hasInitializedServerSelection=true;renderServerDropdown();updateServerViews();Toast.makeText(this,display+" se agregó en este teléfono.",Toast.LENGTH_SHORT).show();}).show();}
    private ServerReading selectedReading(){return serverReadings.isEmpty()?new ServerReading(1,"1",Double.NaN,Double.NaN,Double.NaN,true):serverReadings.get(Math.max(0,Math.min(selectedServerIndex,serverReadings.size()-1)));}
    private void setSelectedServer(int index){if(serverReadings.isEmpty())return;selectedServerIndex=Math.max(0,Math.min(index,serverReadings.size()-1));ServerReading selected=selectedReading();selectedDeviceId=selected.id;temperature=selected.temperature;humidity=selected.humidity;pressure=selected.pressure;updateStatBoxes(humidity,pressure);chartValues.clear();chartValues.addAll(chartHistories.getOrDefault(selectedDeviceId,Collections.emptyList()));if(chart!=null)chart.invalidate();updateServerViews();renderServerDropdown();}
    private void updateServerViews(){if(serverReadings.isEmpty())return;ServerReading selected=selectedReading();if(mainGauge!=null)mainGauge.setTemperature(selected.temperature,threshold);if(serverPickerTitle!=null)serverPickerTitle.setText(selected.name()+(selected.provisional?" · DEMO":""));if(selectedServerBadge!=null){selectedServerBadge.setText(selectedServerIndex==0?"MÁS CALIENTE":"SERVIDOR ACTIVO");selectedServerBadge.setTextColor(selectedServerIndex==0?red:green);}updateStatBoxes(selected.humidity,selected.pressure);chartValues.clear();chartValues.addAll(chartHistories.getOrDefault(selectedDeviceId,Collections.emptyList()));if(chart!=null)chart.invalidate();if(serverDropdownList!=null)renderServerDropdown();}
    private View space(int h){View v=new View(this);return v;}
    private View statBox(String title,String value,boolean humidityMetric){LinearLayout b=column();b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(dp(10),dp(6),dp(8),dp(6));b.setBackground(round(panel,dp(13),line));LinearLayout titleRow=row();titleRow.addView(new MetricIconView(humidityMetric),new LinearLayout.LayoutParams(dp(24),dp(24)));TextView a=label(title,13,muted,true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,-2);ap.leftMargin=dp(7);titleRow.addView(a,ap);TextView c=label(value,23,text,true);b.addView(titleRow);b.addView(c);return b;}
    private android.graphics.drawable.GradientDrawable round(int color,int radius,int stroke){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);d.setStroke(dp(2),stroke);return d;}
    private void showAlerts(){content.addView(eyebrow("HISTORIAL"));heading("Alertas registradas");TextView desc=label("Fecha, hora y temperatura al superar el umbral.",15,muted,false);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.bottomMargin=dp(14);content.addView(desc,dp1);renderAlerts();}
    private String[] splitAlertDateTime(String raw){
        if(raw==null)return new String[]{"Fecha: no disponible","Hora: no disponible"};
        String value=raw.trim().replace('T',' ');
        int separator=value.indexOf(' ');
        if(separator<0)return new String[]{"Fecha: "+(value.isEmpty()?"no disponible":value),"Hora: no disponible"};
        String date=value.substring(0,separator).trim(),time=value.substring(separator+1).trim();
        if(date.matches("\\d{4}-\\d{2}-\\d{2}")){String[] parts=date.split("-");date=parts[2]+"/"+parts[1]+"/"+parts[0];}
        int zone=time.indexOf('Z');if(zone>=0)time=time.substring(0,zone).trim();
        if(time.length()>5)time=time.substring(0,5);
        date="Fecha: "+date;time="Hora: "+time;
        return new String[]{date.isEmpty()?"Fecha no disponible":date,time.isEmpty()?"Hora no disponible":time};
    }
    private void renderAlerts(){
        if(!activePage.equals("alerts"))return;
        while(content.getChildCount()>3)content.removeViewAt(3);
        if(alerts.length()==0){TextView empty=label("Todavía no hay alertas registradas.",15,muted,false);content.addView(empty);return;}
        for(int i=0;i<alerts.length();i++){
            JSONObject a=alerts.optJSONObject(i);if(a==null)continue;
            boolean isTest=a.optBoolean("is_test",false);
            String deviceName=a.optString("device","").trim();
            if(deviceName.startsWith("Prueba · Este teléfono"))deviceName="SERVIDOR 1";if(deviceName.matches("(?i)^SERVIDOR\\s+\\d+$")){String id=deviceName.replaceAll("(?i)^SERVIDOR\\s+","").trim();deviceName=registeredServerName(this,id);}
            if(deviceName.isEmpty()||deviceName.equalsIgnoreCase("Servidor"))deviceName="SERVIDOR "+a.optInt("server_number",1);
            deviceName=deviceName.toUpperCase(java.util.Locale.ROOT);
            LinearLayout card=column();card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(round(panel,dp(13),line));
            TextView server=label(deviceName,17,red,true);server.setGravity(Gravity.CENTER);server.setPadding(dp(12),dp(8),dp(12),dp(8));server.setBackground(round(lightTheme?Color.rgb(255,239,239):Color.rgb(43,19,21),dp(10),red));card.addView(server);
            TextView title=label(isTest?"PRUEBA DE ALARMA":"TEMPERATURA ALTA",12,isTest?amber:red,true);LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(-1,-2);titleP.topMargin=dp(8);card.addView(title,titleP);
            TextView value=label(String.format(java.util.Locale.getDefault(),"%.1f °C",a.optDouble("temperature",0)),26,text,true);card.addView(value);
            String[] dateTime=splitAlertDateTime(a.optString("created_at",""));TextView date=label(dateTime[0],14,muted,false);TextView time=label(dateTime[1],15,text,true);
            LinearLayout.LayoutParams dateP=new LinearLayout.LayoutParams(-1,-2);dateP.topMargin=dp(4);card.addView(date,dateP);LinearLayout.LayoutParams timeP=new LinearLayout.LayoutParams(-1,-2);timeP.topMargin=dp(2);timeP.bottomMargin=dp(3);card.addView(time,timeP);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(card,p);
        }
    }
    private List<ServerReading> readServerPair(JSONArray rows){
        Map<String,JSONObject> byId=new HashMap<>();if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null){String id=row.optString("id_dispositivo","").trim();if(!id.isEmpty())byId.put(id,row);}}
        List<ServerReading> result=new ArrayList<>();JSONObject first=byId.get("1"),second=byId.get("2");boolean bothOriginalReadings=first!=null&&second!=null&&Double.isFinite(finiteNumber(first,"temperatura"))&&Double.isFinite(finiteNumber(second,"temperatura"));
        for(ServerConfig config:registeredServers){JSONObject row=byId.get(config.id);double temp=row==null?Double.NaN:finiteNumber(row,"temperatura"),h=row==null?Double.NaN:finiteNumber(row,"humedad"),p=row==null?Double.NaN:finiteNumber(row,"presion");boolean demo=false;if(!bothOriginalReadings&&config.id.equals("1")){temp=84;demo=true;}else if(!bothOriginalReadings&&config.id.equals("2")){temp=65;demo=true;}result.add(new ServerReading(config.number,config.id,temp,h,p,demo,config.name));}
        result.sort((a,b)->{if(Double.isFinite(a.temperature)&&Double.isFinite(b.temperature))return Double.compare(b.temperature,a.temperature);if(Double.isFinite(a.temperature))return -1;if(Double.isFinite(b.temperature))return 1;return Integer.compare(a.number,b.number);});return result;
    }
    private void refresh(){
        String base=serverUrl(this);
        io.execute(()->{
            try{
                JSONObject data=getJson(base+"/api/status");
                JSONArray rows=data.optJSONArray("latest");
                boolean legacyDemo=data.optBoolean("demo",false);
                boolean mysqlConnected=data.optBoolean("database_connected",!legacyDemo);
                double limit=finiteNumber(data,"threshold");if(!Double.isFinite(limit)||limit<=0)limit=80;
                List<ServerReading> readings=legacyDemo?provisionalReadings():readServerPair(rows);
                boolean usesProvisional=readings.stream().anyMatch(item->item.provisional);
                Map<String,List<Double>> histories=new HashMap<>();
                try{
                    JSONObject history=getJson(base+"/api/history");
                    if(!history.optBoolean("demo",false)){
                        JSONArray points=history.optJSONArray("points");
                        if(points!=null)for(int i=0;i<points.length();i++){
                            JSONObject point=points.optJSONObject(i);if(point==null)continue;
                            double value=finiteNumber(point,"temperature");if(!Double.isFinite(value))continue;
                            String id=point.optString("id_dispositivo","1");histories.computeIfAbsent(id,key->new ArrayList<>()).add(value);
                        }
                    }
                }catch(Exception ignored){ /* A history failure does not discard current readings. */ }
                final List<ServerReading> ordered=readings;final Map<String,List<Double>> receivedHistories=histories;
                final boolean dbReady=mysqlConnected,provisional=usesProvisional;final double configuredLimit=limit;
                runOnUiThread(()->{
                    String previousId=selectedDeviceId;serverReadings=ordered;knownDeviceIds.clear();if(rows!=null)for(int j=0;j<rows.length();j++){JSONObject known=rows.optJSONObject(j);if(known!=null){String id=known.optString("id_dispositivo","").trim();if(!id.isEmpty())knownDeviceIds.add(id);}}threshold=configuredLimit;databaseConnected=dbReady;provisionalReadings=provisional;chartHistories.clear();chartHistories.putAll(receivedHistories);
                    int preserved=-1;for(int i=0;i<serverReadings.size();i++)if(serverReadings.get(i).id.equals(previousId))preserved=i;
                    selectedServerIndex=hasInitializedServerSelection&&preserved>=0?preserved:0;hasInitializedServerSelection=true;
                    if(connection!=null){
                        connection.setText(provisional?"MODO PRUEBA":!dbReady?"SIN MYSQL":"EN LÍNEA");
                        connection.setTextColor(provisional?amber:!dbReady?red:green);
                        int fill=provisional?(lightTheme?Color.rgb(255,246,222):Color.rgb(48,38,20)):!dbReady?(lightTheme?Color.rgb(255,235,237):Color.rgb(45,18,20)):(lightTheme?Color.rgb(230,247,237):Color.rgb(15,43,31));
                        int stroke=provisional?(lightTheme?Color.rgb(240,214,155):Color.rgb(108,83,35)):!dbReady?(lightTheme?Color.rgb(245,185,190):Color.rgb(102,37,42)):(lightTheme?Color.rgb(173,222,190):Color.rgb(35,102,70));
                        connection.setBackground(round(fill,dp(20),stroke));
                    }
                    if(activePage.equals("alerts"))loadAlerts();
                    if(activePage.equals("home"))updateServerViews();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    serverReadings=provisionalReadings();provisionalReadings=true;databaseConnected=false;
                    if(connection!=null){connection.setText("MODO PRUEBA");connection.setTextColor(amber);connection.setBackground(round(lightTheme?Color.rgb(255,246,222):Color.rgb(48,38,20),dp(20),lightTheme?Color.rgb(240,214,155):Color.rgb(108,83,35)));}
                    if(activePage.equals("home"))updateServerViews();
                });
            }
        });
    }    private void updateStatBoxes(double h,double p){ // Refresh compact values in the two metric cards.
        for(int i=0;i<content.getChildCount();i++){View v=content.getChildAt(i);if(v instanceof LinearLayout){LinearLayout row=(LinearLayout)v;if(row.getChildCount()==3&&row.getChildAt(0) instanceof LinearLayout){setStat(row.getChildAt(0),Double.isFinite(h)?String.format(java.util.Locale.getDefault(),"%.1f %%",h):"Sin datos");setStat(row.getChildAt(2),Double.isFinite(p)?String.format(java.util.Locale.getDefault(),"%.1f hPa",p):"Sin datos");}}}}
    private void setStat(View v,String value){if(v instanceof LinearLayout){LinearLayout box=(LinearLayout)v;if(box.getChildCount()>1&&box.getChildAt(1) instanceof TextView)((TextView)box.getChildAt(1)).setText(value);}}
    private void loadAlerts(){
        localTestAlerts=readLocalTestAlerts();alerts=combineAlerts(localTestAlerts,serverAlerts);renderAlerts();
        // Keep real server incidents when the API is available; local test records remain visible offline.
        String base=serverUrl(this);io.execute(()->{try{JSONObject d=getJson(base+"/api/alerts");JSONArray a=d.optJSONArray("alerts");runOnUiThread(()->{serverAlerts=a==null?new JSONArray():a;alerts=combineAlerts(localTestAlerts,serverAlerts);renderAlerts();});}catch(Exception ignored){/* Local test history works without the server. */}});
    }
    private JSONArray readLocalTestAlerts(){String saved=getSharedPreferences(PREFS,MODE_PRIVATE).getString("local_test_alerts","[]");try{return new JSONArray(saved);}catch(Exception ignored){return new JSONArray();}}
    private JSONArray combineAlerts(JSONArray local,JSONArray remote){JSONArray all=new JSONArray();for(int i=0;i<local.length();i++)all.put(local.opt(i));for(int i=0;i<remote.length();i++)all.put(remote.opt(i));return all;}
    private void saveTestAlert(double testTemperature,String serverName){
        try{JSONObject record=new JSONObject().put("created_at",java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).put("temperature",testTemperature).put("threshold",80).put("device",serverName).put("is_test",true);localTestAlerts=readLocalTestAlerts();JSONArray updated=new JSONArray();updated.put(record);for(int i=0;i<localTestAlerts.length()&&updated.length()<100;i++)updated.put(localTestAlerts.opt(i));localTestAlerts=updated;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("local_test_alerts",localTestAlerts.toString()).apply();alerts=combineAlerts(localTestAlerts,serverAlerts);if(activePage.equals("alerts"))renderAlerts();}
        catch(Exception e){Toast.makeText(this,"No se pudo guardar la prueba en este teléfono.",Toast.LENGTH_LONG).show();}
    }
    private JSONObject getJson(String urlText)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(urlText).openConnection();c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setRequestMethod("GET");try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){StringBuilder s=new StringBuilder();String line;while((line=r.readLine())!=null)s.append(line);return new JSONObject(s.toString());}finally{c.disconnect();}}
    private void editServer(){EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);input.setText(serverUrl(this));new AlertDialog.Builder(this).setTitle("Dirección del servidor").setMessage("XAMPP: http://IP-DE-TU-PC/nexus_mysql · Online: https://tu-dominio/nexus_mysql").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{String u=input.getText().toString().trim().replaceAll("/+$","");getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PREF_URL,u).apply();refresh();if(BuildConfig.FCM_CONFIGURED)registerFcmToken();}).show();}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);}
    private void requestFullScreenAlarmAccess(){if(Build.VERSION.SDK_INT>=34){android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);if(!nm.canUseFullScreenIntent()){new AlertDialog.Builder(this).setTitle("Permitir alerta en pantalla completa").setMessage("Para mostrar el aviso rojo incluso cuando TempQuest está en segundo plano, habilitá las alarmas de pantalla completa en Ajustes.").setNegativeButton("Ahora no",null).setPositiveButton("Abrir ajustes",(d,w)->{try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}}).show();}}}
    private void createLocalChannels(){if(Build.VERSION.SDK_INT>=26){android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);android.net.Uri sound=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);android.app.NotificationChannel alarm=new android.app.NotificationChannel(AlarmPlaybackService.CHANNEL,"Alarmas de temperatura",android.app.NotificationManager.IMPORTANCE_HIGH);alarm.enableVibration(true);alarm.setSound(sound,new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build());nm.createNotificationChannel(alarm);android.app.NotificationChannel ongoing=new android.app.NotificationChannel(AlarmPlaybackService.ONGOING_CHANNEL,"Alarma activa",android.app.NotificationManager.IMPORTANCE_HIGH);ongoing.enableVibration(true);ongoing.setVibrationPattern(new long[]{0,650,250,650,600});nm.createNotificationChannel(ongoing);}}
    private void registerFcmToken(){if(!BuildConfig.FCM_CONFIGURED)return;com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->io.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(serverUrl(this)+"/api/register-device").openConnection();c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.getOutputStream().write(new JSONObject().put("token",token).toString().getBytes(StandardCharsets.UTF_8));c.getInputStream().close();c.disconnect();}catch(Exception e){android.util.Log.w("TempQuest","No se pudo registrar FCM",e);}}));}
    private void startAlarm(String detail){ServerReading selected=selectedReading();startAlarm(detail,temperature,selected.number,selected.name());}
    private void startAlarm(String detail,double alarmTemperature){ServerReading selected=selectedReading();startAlarm(detail,alarmTemperature,selected.number,selected.name());}
    private void startAlarm(String detail,double alarmTemperature,int serverNumber){startAlarm(detail,alarmTemperature,serverNumber,registeredServerNameByNumber(serverNumber));}
    private String registeredServerNameByNumber(int number){for(ServerConfig item:registeredServers)if(item.number==number)return item.name;return "SERVIDOR "+number;}
    private void startAlarm(String detail,double alarmTemperature,int serverNumber,String serverName){Intent i=new Intent(this,AlarmPlaybackService.class).setAction(AlarmPlaybackService.ACTION_START).putExtra("detail",detail).putExtra("temperature",alarmTemperature).putExtra("server_number",serverNumber).putExtra("server_name",serverName);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Intent screen=new Intent(this,AlarmActivity.class).putExtra("detail",detail).putExtra("temperature",alarmTemperature).putExtra("server_number",serverNumber).putExtra("server_name",serverName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(screen);}

    private class ChevronToggleView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean expanded=false;
        ChevronToggleView(){super(MainActivity.this);setContentDescription("Abrir o cerrar lista de servidores");}
        void setExpanded(boolean value){expanded=value;invalidate();setContentDescription(value?"Cerrar lista de servidores":"Abrir lista de servidores");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f;paint.setStyle(Paint.Style.STROKE);paint.setColor(red);paint.setStrokeWidth(dp(3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path chevron=new Path();if(expanded){chevron.moveTo(cx-dp(7),cy+dp(2));chevron.cubicTo(cx-dp(5),cy+dp(2),cx-dp(2),cy-dp(5),cx,cy-dp(5));chevron.cubicTo(cx+dp(2),cy-dp(5),cx+dp(5),cy+dp(2),cx+dp(7),cy+dp(2));}else{chevron.moveTo(cx-dp(7),cy-dp(2));chevron.cubicTo(cx-dp(5),cy-dp(2),cx-dp(2),cy+dp(5),cx,cy+dp(5));chevron.cubicTo(cx+dp(2),cy+dp(5),cx+dp(5),cy-dp(2),cx+dp(7),cy-dp(2));}canvas.drawPath(chevron,paint);}
    }
    private abstract class IconView extends View {
        final Paint icon=new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(){super(MainActivity.this);icon.setStyle(Paint.Style.STROKE);icon.setStrokeCap(Paint.Cap.ROUND);icon.setStrokeJoin(Paint.Join.ROUND);}
        void stroke(int color,float width){icon.setColor(color);icon.setStrokeWidth(dp(width));icon.setStyle(Paint.Style.STROKE);}
    }
    private class ThemeToggleView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ThemeToggleView(){super(MainActivity.this);setClickable(true);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cy=getHeight()/2f,mx=getWidth()*.34f,sx=getWidth()*.70f;int active=lightTheme?red:Color.rgb(231,235,237),inactive=muted;
            paint.setStyle(Paint.Style.FILL);paint.setColor(lightTheme?inactive:active);c.drawCircle(mx,cy,dp(6),paint);paint.setColor(panel);c.drawCircle(mx+dp(3),cy-dp(2),dp(5.5f),paint);
            int sun=lightTheme?active:inactive;paint.setColor(sun);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.7f));paint.setStrokeCap(Paint.Cap.ROUND);c.drawCircle(sx,cy,dp(4),paint);for(int i=0;i<8;i++){double a=Math.PI*i/4;float x1=sx+(float)Math.cos(a)*dp(7),y1=cy+(float)Math.sin(a)*dp(7),x2=sx+(float)Math.cos(a)*dp(9),y2=cy+(float)Math.sin(a)*dp(9);c.drawLine(x1,y1,x2,y2,paint);}
        }
    }
    private class BrandIconView extends IconView {
        BrandIconView(){super();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float x=dp(3),w=getWidth()-dp(6),h=dp(9);stroke(red,1.8f);c.drawRoundRect(x,dp(4),x+w,dp(4)+h,dp(2),dp(2),icon);c.drawRoundRect(x,dp(16),x+w,dp(16)+h,dp(2),dp(2),icon);icon.setStyle(Paint.Style.FILL);c.drawCircle(dp(7),dp(8),dp(1),icon);c.drawCircle(dp(7),dp(20),dp(1),icon);c.drawLine(dp(11),dp(8),dp(18),dp(8),icon);c.drawLine(dp(11),dp(20),dp(18),dp(20),icon);}
    }
    private class MetricIconView extends IconView {
        private final boolean humidityMetric;
        MetricIconView(boolean humidity){super();humidityMetric=humidity;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f;
            if(humidityMetric){stroke(Color.rgb(67,164,255),2.2f);Path drop=new Path();drop.moveTo(cx,cy-dp(9));drop.cubicTo(cx-dp(3),cy-dp(4),cx-dp(8),cy+dp(1),cx-dp(7),cy+dp(5));drop.cubicTo(cx-dp(6),cy+dp(13),cx+dp(6),cy+dp(13),cx+dp(7),cy+dp(5));drop.cubicTo(cx+dp(8),cy+dp(1),cx+dp(3),cy-dp(4),cx,cy-dp(9));c.drawPath(drop,icon);}
            else {stroke(Color.rgb(166,125,255),2.2f);RectF gauge=new RectF(cx-dp(10),cy-dp(9),cx+dp(10),cy+dp(11));c.drawArc(gauge,200,140,false,icon);c.drawLine(cx,cy+dp(2),cx+dp(5),cy-dp(4),icon);icon.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy+dp(2),dp(1.7f),icon);}
        }
    }

    private class NavIconView extends View {
        private final boolean home; private final int color; private final Paint iconPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        NavIconView(boolean isHome,int tint){super(MainActivity.this);home=isHome;color=tint;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f;
            iconPaint.setColor(color);iconPaint.setStyle(Paint.Style.STROKE);iconPaint.setStrokeWidth(dp(2.1f));iconPaint.setStrokeCap(Paint.Cap.ROUND);iconPaint.setStrokeJoin(Paint.Join.ROUND);
            Path shape=new Path();
            if(home){shape.moveTo(cx-dp(10),cy);shape.lineTo(cx,cy-dp(9));shape.lineTo(cx+dp(10),cy);shape.lineTo(cx+dp(8),cy);shape.lineTo(cx+dp(8),cy+dp(9));shape.lineTo(cx+dp(2),cy+dp(9));shape.lineTo(cx+dp(2),cy+dp(3));shape.lineTo(cx-dp(2),cy+dp(3));shape.lineTo(cx-dp(2),cy+dp(9));shape.lineTo(cx-dp(8),cy+dp(9));shape.lineTo(cx-dp(8),cy);shape.close();canvas.drawPath(shape,iconPaint);}
            else {shape.moveTo(cx-dp(9),cy+dp(7));shape.quadTo(cx-dp(6),cy+dp(4),cx-dp(6),cy);shape.lineTo(cx-dp(6),cy-dp(4));shape.quadTo(cx,cy-dp(12),cx+dp(6),cy-dp(4));shape.lineTo(cx+dp(6),cy);shape.quadTo(cx+dp(6),cy+dp(4),cx+dp(9),cy+dp(7));shape.close();canvas.drawPath(shape,iconPaint);canvas.drawLine(cx-dp(9),cy+dp(8),cx+dp(9),cy+dp(8),iconPaint);iconPaint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy+dp(11),dp(1.7f),iconPaint);}
        }
    }

    private class GaugeView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private double value=Double.NaN,threshold=80;
        private float radius,arcTop,arcCenterY;
        GaugeView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setTemperature(double v,double limit){value=Double.isFinite(v)?v:Double.NaN;threshold=Double.isFinite(limit)&&limit>0?limit:80;invalidate();}
        @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
            int width=MeasureSpec.getSize(widthMeasureSpec);
            float availableRadius=Math.max(dp(70),(width-dp(46))/2f);
            radius=Math.min(dp(166),availableRadius);
            int desiredHeight=Math.round(radius+dp(91));
            int height=resolveSize(desiredHeight,heightMeasureSpec);
            setMeasuredDimension(width,height);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f;arcTop=dp(7);arcCenterY=arcTop+radius;
            RectF arc=new RectF(cx-radius,arcTop,cx+radius,arcTop+radius*2);
            // A thin dark outline remains visible without data; valid measurements paint a thicker colored trace.
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(lightTheme?Color.BLACK:line);c.drawArc(arc,180,180,false,p);
            if(Double.isFinite(value)){
                float sweep=(float)Math.max(0,Math.min(180,value/GAUGE_MAX_C*180));
                p.setStrokeWidth(dp(15));
                float thresholdStop=(float)Math.max(.12,Math.min(.98,threshold/GAUGE_MAX_C));
                float yellowStop=thresholdStop*.72f, limeStop=thresholdStop*.5f;
                p.setShader(new android.graphics.LinearGradient(cx-radius,arcCenterY-radius,cx+radius,arcCenterY-radius,
                        new int[]{green,Color.rgb(194,218,45),Color.rgb(246,201,42),amber,red},
                        new float[]{0,limeStop,yellowStop,thresholdStop,1},android.graphics.Shader.TileMode.CLAMP));
                c.drawArc(arc,180,sweep,false,p);p.setShader(null);
            }
            // Thermometer icon is drawn as vector strokes so it stays sharp at every screen density.
            float tubeTop=arcTop+dp(13),tubeBottom=arcTop+dp(31),bulbY=arcTop+dp(35);
            p.setColor(red);p.setStrokeWidth(dp(2.5f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            c.drawRoundRect(new RectF(cx-dp(4),tubeTop,cx+dp(4),tubeBottom),dp(4),dp(4),p);c.drawCircle(cx,bulbY,dp(6),p);
            p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(cx-dp(1.5f),arcTop+dp(22),cx+dp(1.5f),bulbY),dp(1.5f),dp(1.5f),p);c.drawCircle(cx,bulbY,dp(3.5f),p);
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(dp(12));p.setColor(lightTheme?Color.rgb(53,64,74):muted);p.setTextAlign(Paint.Align.CENTER);
            c.drawText("TEMP. DEL SERVIDOR",cx,arcTop+radius*.59f,p);
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(dp(Double.isFinite(value)?40:24));p.setColor(Double.isFinite(value)?text:muted);
            c.drawText(Double.isFinite(value)?formatTemperature(value):"Sin datos",cx,arcCenterY+dp(37),p);
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(dp(12));p.setColor(lightTheme?Color.rgb(53,64,74):muted);
            p.setTextAlign(Paint.Align.LEFT);c.drawText("0 °C",cx-radius+dp(2),arcCenterY+dp(61),p);
            p.setTextAlign(Paint.Align.RIGHT);c.drawText("100 °C",cx+radius-dp(2),arcCenterY+dp(61),p);
            p.setTextAlign(Paint.Align.CENTER);
        }
    }
    private class HistoryChartView extends View {private final Paint p=new Paint(3);HistoryChartView(Context c){super(c);}@Override protected void onDraw(Canvas c){super.onDraw(c);float l=dp(55),r=getWidth()-dp(7),t=dp(10),b=getHeight()-dp(12);p.setStrokeWidth(dp(1));p.setTextSize(dp(11));p.setTypeface(Typeface.DEFAULT);p.setTextAlign(Paint.Align.RIGHT);for(int i=0;i<=5;i++){float y=t+(b-t)*i/5;p.setColor(line);c.drawLine(l,y,r,y,p);p.setColor(muted);c.drawText((100-i*20)+" °C",l-dp(7),y+dp(4),p);}if(chartValues.size()<2)return;double min=0,max=100;p.setTextAlign(Paint.Align.LEFT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(green);android.graphics.Path path=new android.graphics.Path();for(int i=0;i<chartValues.size();i++){float x=l+(r-l)*i/(chartValues.size()-1);float y=b-(float)(chartValues.get(i)-min)/(float)(max-min)*(b-t);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);float hy=b-(float)(threshold-min)/(float)(max-min)*(b-t);p.setColor(amber);p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(5),dp(4)},0));c.drawLine(l,hy,r,hy,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);}}
}







