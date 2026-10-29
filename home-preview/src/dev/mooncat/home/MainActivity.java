package dev.mooncat.home;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
public class MainActivity extends Activity {
 private final Handler handler=new Handler(); private TextView clock; private boolean active;
 private final Runnable tick=new Runnable(){public void run(){if(!active)return;clock.setText(new SimpleDateFormat("h:mm a",Locale.ENGLISH).format(new Date()));handler.postDelayed(this,15000);}};
 private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
 private GradientDrawable bg(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(6|4096);
 LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(48),dp(30),dp(48),dp(24));root.setBackgroundColor(Color.BLACK);setContentView(root);
 LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(55)));
 LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);TextView title=text("MOONCAT HOME",16,0xfff3f3f3);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setLetterSpacing(.16f);brand.addView(title);brand.addView(text("Your space. Just the essentials.",12,0xff797d86));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
 clock=text("",24,0xffe3e5e9);header.addView(clock);
 Space gap=new Space(this);root.addView(gap,new LinearLayout.LayoutParams(1,0,1));
 TextView heading=text("What would you like to watch?",26,0xfff5f5f5);root.addView(heading);
 TextView sub=text("Pick an app to get started",13,0xff81858f);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(6);sp.bottomMargin=dp(24);root.addView(sub,sp);
 LinearLayout cards=new LinearLayout(this);root.addView(cards,new LinearLayout.LayoutParams(-1,dp(156)));
 String[] labels={"YouTube","Hotstar","MoonCat"};String[] packages={"com.amazon.firetv.youtube","in.startv.hotstar","dev.mooncat.tv.v2"};int[] accents={0xffff4545,0xff60a5fa,0xffc4b5fd};
 for(int i=0;i<3;i++){final String pkg=packages[i];final int accent=accents[i];LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER);card.setFocusable(true);card.setClickable(true);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(bg(0xff101114,0xff24262c));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)cp.leftMargin=dp(18);cards.addView(card,cp);
 ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(Exception ignored){icon.setImageResource(android.R.drawable.ic_media_play);}card.addView(icon,new LinearLayout.LayoutParams(dp(96),dp(70)));
 TextView label=text(labels[i],18,0xffe9e9ec);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(12);card.addView(label,lp);
 card.setContentDescription("Open "+labels[i]);card.setOnClickListener(v->launch(pkg));card.setOnFocusChangeListener((v,focus)->{card.setBackground(bg(focus?0xff1b1d24:0xff101114,focus?accent:0xff24262c));card.animate().scaleX(focus?1.035f:1f).scaleY(focus?1.035f:1f).setDuration(110).start();});if(i==0)card.requestFocus();}
 root.addView(new Space(this),new LinearLayout.LayoutParams(1,0,1));
 LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);root.addView(footer,new LinearLayout.LayoutParams(-1,dp(42)));
 TextView hint=text("Use the remote to choose an app",11,0xff626771);footer.addView(hint,new LinearLayout.LayoutParams(0,-2,1));
 Button settings=new Button(this);settings.setText("Settings");settings.setTextSize(12);settings.setAllCaps(false);footer.addView(settings);settings.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}catch(Exception e){Toast.makeText(this,"Press Home to open Amazon Settings",Toast.LENGTH_LONG).show();}});
 Button home=new Button(this);home.setText("Amazon Home");home.setTextSize(12);home.setAllCaps(false);footer.addView(home);home.setOnClickListener(v->{Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage("com.amazon.tv.launcher");try{startActivity(intent);}catch(Exception e){finish();}});
 }
 private void launch(String pkg){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(i==null)i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Unable to open app",Toast.LENGTH_SHORT).show();}}else Toast.makeText(this,"App is not installed",Toast.LENGTH_SHORT).show();}
 @Override protected void onResume(){super.onResume();active=true;handler.removeCallbacks(tick);tick.run();}
 @Override protected void onPause(){active=false;handler.removeCallbacks(tick);super.onPause();}
}
