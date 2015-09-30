package edu.ncsu.csc450.intelligentalarm;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.widget.Toast;

@SuppressWarnings("unused")
public class AlarmReceiver extends WakefulBroadcastReceiver {

  @Override
  public void onReceive(final Context context, Intent intent) {
    // setLooping(true)
    /*Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    if (alarmUri == null) {
      alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }
    Ringtone ringtone = RingtoneManager.getRingtone(context, alarmUri);
    ringtone.play();*/
    
    Toast.makeText(context, "Wake Up! Wake Up!", Toast.LENGTH_LONG).show();

    ComponentName comp = new ComponentName(context.getPackageName(), AlarmService.class.getName());
    startWakefulService(context, (intent.setComponent(comp)));
    setResultCode(Activity.RESULT_OK);    
  }
}
