package dev.mooncat.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    final int BG=Color.rgb(8,12,28), ACCENT=Color.rgb(164,144,248), FG=Color.WHITE;
    LinearLayout root, page, nav;
    JSONArray studios;
    VideoView video;
    String[] names={"DD News","Aaj Tak","ABP News","India Today"};
    String[] urls={
       "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/0811cd8c37ca4c409d5385a6cd2fa18b/index.m3u8",
       "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8",
       "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-472500/abpnews/master.m3u8",
       "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-293160/master.m3u8"
    };
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable bg(int color, int radius) {
       GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    @Override public void onCreate(Bundle saved){
       super.onCreate(saved);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
       try{ByteArrayOutputStream b=new ByteArrayOutputStream();InputStream in=getAssets().open("studios.json");byte[] c=new byte[8192];int n;while((n=in.read(c))>0)b.write(c,0,n);studios=new JSONArray(b.toString("UTF-8"));}catch(Exception e){studios=new JSONArray();}
       home();
    }
    TextView text(String t,int size,int color,boolean bold){
       TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(6),dp(7),dp(6),dp(7));
       if(bold)v.setTypeface(null,Typeface.BOLD);return v;
    }
    void base(String section,boolean tabs){
       root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);setContentView(root);
       LinearLayout bar=new LinearLayout(this);bar.setPadding(dp(14),dp(13),dp(14),dp(9));bar.setGravity(Gravity.CENTER_VERTICAL);
       ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.mooncat_icon);bar.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
       TextView brand=text("MoonCat",25,FG,true);bar.addView(brand);root.addView(bar);
       ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setPadding(dp(18),dp(8),dp(18),dp(24));page.setOrientation(LinearLayout.VERTICAL);
       scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
       if(tabs){nav=new LinearLayout(this);nav.setPadding(dp(4),dp(8),dp(4),dp(10));nav.setBackgroundColor(Color.rgb(18,24,45));
          tab("Home",()->home());tab("Studios",()->studios());tab("News",()->news());root.addView(nav);}
       page.addView(text(section,24,FG,true));
    }
    void tab(String label,Runnable action){
       TextView t=text(label,15,FG,true);t.setGravity(Gravity.CENTER);t.setOnClickListener(v->action.run());
       nav.addView(t,new LinearLayout.LayoutParams(0,dp(45),1));
    }
    void action(LinearLayout target,String name,String subtitle,Runnable runnable){
       LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(13),dp(13),dp(13));
       card.setBackground(bg(Color.rgb(22,28,49),16));
       TextView title=text(name,19,FG,true);card.addView(title);
       if(subtitle!=null)card.addView(text(subtitle,13,Color.rgb(186,193,218),false));
       card.setOnClickListener(v->runnable.run());
       LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);target.addView(card,lp);
    }
    void home(){
       stop();base("Discover",true);
       page.addView(text("Explore films and series by studio, or watch live news.",15,Color.LTGRAY,false));
       action(page,"Studios & Franchises","Marvel, DC, Harry Potter, HBO, Paramount and more",()->studios());
       action(page,"Live News","Inside MoonCat — Hindi and English channels",()->news());
       page.addView(text("Featured collections",20,ACCENT,true));
       for(int i=0;i<Math.min(5,studios.length());i++){final int j=i;JSONObject o=studios.optJSONObject(i);
           action(page,o.optString("name"),"Movies and series",()->studio(j));}
    }
    void studios(){
       stop();base("Studios & Collections",true);
       for(int i=0;i<studios.length();i++){final int j=i;JSONObject s=studios.optJSONObject(i);
           action(page,s.optString("name"),s.optJSONArray("works").length()+" featured titles",()->studio(j));}
    }
    void studio(int index){
       JSONObject s=studios.optJSONObject(index);if(s==null)return;
       base(s.optString("name"),true);
       action(page,"← All Studios",null,()->studios());
       JSONArray items=s.optJSONArray("works");
       for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);
           if(item==null)continue;
           action(page,item.optString("title"),item.optString("type").equals("series")?"Series":"Movie",()->detail(item));}
    }
    void detail(JSONObject item){
       base(item.optString("title"),true);
       page.addView(text(item.optString("type").toUpperCase(Locale.ROOT),14,ACCENT,true));
       page.addView(text("IMDb ID: "+item.optString("imdb"),14,Color.LTGRAY,false));
       page.addView(text("Browse this title in MoonCat. Movie and series playback source integration is not yet available in the mobile beta.",16,FG,false));
       action(page,"Browse more collections",null,()->studios());
    }
    void news(){
       stop();base("Live News",true);
       page.addView(text("Streams play within this app. Channel availability depends on broadcasters.",14,Color.LTGRAY,false));
       for(int i=0;i<names.length;i++){final int j=i;action(page,names[i],"Tap to watch live",()->play(j));}
    }
    void play(int ix){
       stop();base(names[ix]+" · LIVE",false);
       LinearLayout controls=new LinearLayout(this);
       page.addView(text("Live channel — in-app playback",16,ACCENT,true));
       video=new VideoView(this);video.setBackgroundColor(Color.BLACK);
       page.addView(video,new LinearLayout.LayoutParams(-1,dp(260)));
       MediaController mc=new MediaController(this);mc.setAnchorView(video);video.setMediaController(mc);
       video.setOnErrorListener((mp,what,extra)->{page.addView(text("Stream unavailable. Try another channel.",15,Color.RED,false));return true;});
       try{video.setVideoURI(android.net.Uri.parse(urls[ix]));video.start();}
       catch(Exception e){page.addView(text("Unable to play stream.",16,Color.RED,false));}
       action(page,"← Back to News",null,()->news());
    }
    void stop(){if(video!=null){try{video.stopPlayback();}catch(Exception e){}video=null;}}
    @Override public void onBackPressed(){if(video!=null)news();else home();}
    @Override protected void onStop(){super.onStop();stop();}
}

