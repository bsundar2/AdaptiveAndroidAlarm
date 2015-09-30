package edu.ncsu.csc450.intelligentalarm;

import jade.android.AndroidHelper;
import jade.android.MicroRuntimeService;
import jade.android.MicroRuntimeServiceBinder;
import jade.android.RuntimeCallback;
import jade.core.MicroRuntime;
import jade.core.Profile;
import jade.util.Logger;
import jade.util.leap.Properties;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.logging.Level;

import edu.ncsu.csc450.intelligentalarm.agent.AlarmClientAgent;
import edu.ncsu.csc450.intelligentalarm.agent.AlarmClientInterface;
import edu.ncsu.csc450.intelligentalarm.settings.SettingsActivity;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class AlarmActivity extends Activity {

  private Logger logger = Logger.getJADELogger(this.getClass().getName());

  static final int SETTINGS_REQUEST = 1;

  private double userLatitude = 35.771888;
  private double userLongitude = -78.673573;

  private MicroRuntimeServiceBinder microRuntimeServiceBinder;
  private ServiceConnection serviceConnection;

  private MyReceiver myReceiver;
  private MyHandler myHandler;

  private AlarmClientInterface alarmClientInterface;

  private AlarmManager alarmManager;
  private PendingIntent pendingIntent;

  private static volatile int alarmYear = -1;
  private static volatile int alarmMonth, alarmDayOfMonth, alarmHourOfDay, alarmMinute;
  private SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a",
      Locale.US);

  private LocationManager locationManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      LocationListener locationListener = new MyLocationListener();
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 100,
          locationListener);
    }

    myReceiver = new MyReceiver();

    IntentFilter killFilter = new IntentFilter();
    killFilter.addAction("edu.ncsu.csc450.intelligentalarm.KILL");
    registerReceiver(myReceiver, killFilter);

    IntentFilter weatherUpdateFilter = new IntentFilter();
    weatherUpdateFilter.addAction("edu.ncsu.csc450.intelligentalarm.WEATHER_UPDATE");
    registerReceiver(myReceiver, weatherUpdateFilter);
    
    IntentFilter delayUpdateFilter = new IntentFilter();
    delayUpdateFilter.addAction("edu.ncsu.csc450.intelligentalarm.FLIGHT_SCHEDULE");
    registerReceiver(myReceiver, delayUpdateFilter);

    IntentFilter friendUpdateFilter = new IntentFilter();
    friendUpdateFilter.addAction("edu.ncsu.csc450.intelligentalarm.FRIEND_UPDATE");
    registerReceiver(myReceiver, friendUpdateFilter);

    myHandler = new MyHandler(this);

    SharedPreferences settings = getSharedPreferences("jadeAlarmPrefsFile", 0);
    String host = settings.getString("defaultHost", "");
    String port = settings.getString("defaultPort", "");

    try {
      setupAgent("U-Alice", host, port, agentStartupCallback);
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Unexpected exception creating chat agent!");
    }

    setContentView(R.layout.activity_alarm);

    Intent alarmIntent = new Intent(AlarmActivity.this, AlarmReceiver.class);
    pendingIntent = PendingIntent.getBroadcast(AlarmActivity.this, 0, alarmIntent, 0);

    final Calendar c = Calendar.getInstance();
    if (alarmYear == -1) {
      alarmYear = c.get(Calendar.YEAR);
      alarmMonth = c.get(Calendar.MONTH);
      alarmDayOfMonth = c.get(Calendar.DAY_OF_MONTH);
      alarmHourOfDay = c.get(Calendar.HOUR_OF_DAY);
      alarmMinute = c.get(Calendar.MINUTE);
    } else {
      c.set(Calendar.YEAR, alarmYear);
      c.set(Calendar.MONTH, alarmMonth);
      c.set(Calendar.DAY_OF_MONTH, alarmDayOfMonth);
      c.set(Calendar.HOUR_OF_DAY, alarmHourOfDay);
      c.set(Calendar.MINUTE, alarmMinute);
    }
    TextView dateEt = (TextView) findViewById(R.id.dateTv);
    dateEt.setText(alarmYear + "/" + (alarmMonth + 1) + "/" + alarmDayOfMonth);
    TextView timeEt = (TextView) findViewById(R.id.timeTv);
    timeEt.setText(alarmHourOfDay + ":" + alarmMinute);

    Button btnSetAlarm = (Button) findViewById(R.id.btnSetAlarm);
    btnSetAlarm.setOnClickListener(btnSetAlarmListener);

    Button btnTestWeather = (Button) findViewById(R.id.btnTestWeather);
    btnTestWeather.setOnClickListener(btnTestWeatherListener);

    Button btnTestFlightSchedule = (Button) findViewById(R.id.btnTestFlightSchedule);
    btnTestFlightSchedule.setOnClickListener(btnTestFlightScheduleListener);

    Button btnTestSnooze = (Button) findViewById(R.id.btnTestSnooze);
    btnTestSnooze.setOnClickListener(btnTestSnoozeListener);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    logger.log(Level.INFO, "Destroy activity!");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_settings:
      Intent showSettings = new Intent(AlarmActivity.this, SettingsActivity.class);
      AlarmActivity.this.startActivityForResult(showSettings, SETTINGS_REQUEST);
      return true;
    case R.id.menu_exit:
      finish();
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  private OnClickListener btnSetAlarmListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      Calendar calendar = Calendar.getInstance();
      calendar.set(Calendar.YEAR, alarmYear);
      calendar.set(Calendar.MONTH, alarmMonth);
      calendar.set(Calendar.DAY_OF_MONTH, alarmDayOfMonth);
      calendar.set(Calendar.HOUR_OF_DAY, alarmHourOfDay);
      calendar.set(Calendar.MINUTE, alarmMinute);

      setAlarm("Alarm set for ", calendar, false);
    }
  };

  private void setAlarm(String msgPrefix, Calendar cal, boolean cancelPending) {
    if (cancelPending) {
      alarmManager.cancel(pendingIntent);
    }
    
    alarmYear = cal.get(Calendar.YEAR);
    alarmMonth = cal.get(Calendar.MONTH);
    alarmDayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
    alarmHourOfDay = cal.get(Calendar.HOUR_OF_DAY);
    alarmMinute = cal.get(Calendar.MINUTE);
    
    TextView dateEt = (TextView) findViewById(R.id.dateTv);
    dateEt.setText(alarmYear + "/" + (alarmMonth + 1) + "/" + alarmDayOfMonth);
    TextView timeEt = (TextView) findViewById(R.id.timeTv);
    timeEt.setText(alarmHourOfDay + ":" + alarmMinute);
    
    alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
    Toast.makeText(AlarmActivity.this, msgPrefix + dateFormat.format(cal.getTime()),
        Toast.LENGTH_SHORT).show();

  }

  private OnClickListener btnTestWeatherListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      try {
        alarmClientInterface = MicroRuntime.getAgent("U-Alice").getO2AInterface(
            AlarmClientInterface.class);
        alarmClientInterface.getWeatherUpdate();
      } catch (StaleProxyException e) {
        e.printStackTrace();
      } catch (ControllerException e) {
        e.printStackTrace();
      }
    }
  };

  private OnClickListener btnTestFlightScheduleListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      // Toast.makeText(AlarmActivity.this, "TODO! IMPLEMENT THIS.", Toast.LENGTH_LONG).show();
    	try {
            alarmClientInterface = MicroRuntime.getAgent("U-Alice").getO2AInterface(
                AlarmClientInterface.class);
            alarmClientInterface.getDelayTimeUpdate();
          } catch (StaleProxyException e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Stale Proxy Exception");
          } catch (ControllerException e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Controller Exception");
          }
    }
  };

  private OnClickListener btnTestSnoozeListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      try {
        alarmClientInterface = MicroRuntime.getAgent("U-Alice").getO2AInterface(
            AlarmClientInterface.class);
        alarmClientInterface.findAFriendNearby(userLatitude, userLongitude);
      } catch (StaleProxyException e) {
        e.printStackTrace();
      } catch (ControllerException e) {
        e.printStackTrace();
      }
    }
  };

  public void showDatePickerDialog(View v) {
    DialogFragment newFragment = new DatePickerFragment();
    newFragment.show(getFragmentManager(), "datePicker");
  }

  public void showTimePickerDialog(View v) {
    DialogFragment newFragment = new TimePickerFragment();
    newFragment.show(getFragmentManager(), "timePicker");
  }

  public class DatePickerFragment extends DialogFragment implements
      DatePickerDialog.OnDateSetListener {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Calendar c = Calendar.getInstance();
      alarmYear = c.get(Calendar.YEAR);
      alarmMonth = c.get(Calendar.MONTH);
      alarmDayOfMonth = c.get(Calendar.DAY_OF_MONTH);

      return new DatePickerDialog(getActivity(), this, alarmYear, alarmMonth, alarmDayOfMonth);
    }

    public void onDateSet(DatePicker view, int _year, int _month, int _day) {
      alarmDayOfMonth = _day;
      alarmMonth = _month;
      alarmYear = _year;
      TextView dateEt = (TextView) findViewById(R.id.dateTv);
      dateEt.setText(alarmYear + "/" + (alarmMonth + 1) + "/" + alarmDayOfMonth);
    }
  }

  public class TimePickerFragment extends DialogFragment implements
      TimePickerDialog.OnTimeSetListener {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Calendar c = Calendar.getInstance();
      alarmHourOfDay = c.get(Calendar.HOUR_OF_DAY);
      alarmMinute = c.get(Calendar.MINUTE);

      return new TimePickerDialog(getActivity(), this, alarmHourOfDay, alarmMinute,
          DateFormat.is24HourFormat(getActivity()));
    }

    public void onTimeSet(TimePicker view, int _hourOfDay, int _minute) {
      alarmHourOfDay = _hourOfDay;
      alarmMinute = _minute;
      TextView timeEt = (TextView) findViewById(R.id.timeTv);
      timeEt.setText(alarmHourOfDay + ":" + alarmMinute);
    }
  }

  public void setupAgent(final String nickname, final String host, final String port,
      final RuntimeCallback<AgentController> agentStartupCallback) {

    final Properties profile = new Properties();
    profile.setProperty(Profile.MAIN_HOST, host);
    profile.setProperty(Profile.MAIN_PORT, port);
    profile.setProperty(Profile.MAIN, Boolean.FALSE.toString());
    profile.setProperty(Profile.JVM, Profile.ANDROID);

    if (AndroidHelper.isEmulator()) {
      profile.setProperty(Profile.LOCAL_HOST, AndroidHelper.LOOPBACK);
      profile.setProperty(Profile.LOCAL_PORT, "2000");
    } else {
      profile.setProperty(Profile.LOCAL_HOST, AndroidHelper.getLocalIPAddress());
    }

    if (microRuntimeServiceBinder == null) {
      serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
          microRuntimeServiceBinder = (MicroRuntimeServiceBinder) service;
          logger.log(Level.INFO, "Gateway successfully bound to MicroRuntimeService");
          startContainer(nickname, profile, agentStartupCallback);
        };

        public void onServiceDisconnected(ComponentName className) {
          microRuntimeServiceBinder = null;
          logger.log(Level.INFO, "Gateway unbound from MicroRuntimeService");
        }
      };
      logger.log(Level.INFO, "Binding Gateway to MicroRuntimeService...");
      bindService(new Intent(getApplicationContext(), MicroRuntimeService.class),
          serviceConnection, Context.BIND_AUTO_CREATE);
    } else {
      logger.log(Level.INFO, "MicroRumtimeGateway already binded to service");
      startContainer(nickname, profile, agentStartupCallback);
    }
  }

  private RuntimeCallback<AgentController> agentStartupCallback = new RuntimeCallback<AgentController>() {
    @Override
    public void onSuccess(AgentController agent) {
    }

    @Override
    public void onFailure(Throwable throwable) {
      logger.log(Level.INFO, "Nickname already in use!");
      myHandler.postError("nickname in use"); // getString(R.string.msg_nickname_in_use)
    }
  };

  private void startContainer(final String nickname, Properties profile,
      final RuntimeCallback<AgentController> agentStartupCallback) {
    if (!MicroRuntime.isRunning()) {
      microRuntimeServiceBinder.startAgentContainer(profile, new RuntimeCallback<Void>() {
        @Override
        public void onSuccess(Void thisIsNull) {
          logger.log(Level.INFO, "Successful start of the container...");
          startAgent(nickname, agentStartupCallback);
        }

        @Override
        public void onFailure(Throwable throwable) {
          logger.log(Level.SEVERE, "Failed to start the container...");
        }
      });
    } else {
      startAgent(nickname, agentStartupCallback);
    }
  }

  private void startAgent(final String nickname,
      final RuntimeCallback<AgentController> agentStartupCallback) {
    microRuntimeServiceBinder.startAgent(nickname, AlarmClientAgent.class.getName(),
        new Object[] { getApplicationContext() }, new RuntimeCallback<Void>() {
          @Override
          public void onSuccess(Void thisIsNull) {
            logger.log(Level.INFO, "Successful start of the " + AlarmClientAgent.class.getName()
                + "...");
            try {
              agentStartupCallback.onSuccess(MicroRuntime.getAgent(nickname));
            } catch (ControllerException e) {
              agentStartupCallback.onFailure(e);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            logger.log(Level.SEVERE, "Failed to start the " + AlarmClientAgent.class.getName()
                + "...");
            agentStartupCallback.onFailure(throwable);
            throwable.printStackTrace();
          }
        });
  }

  public void ShowDialog(String message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(AlarmActivity.this);
    builder.setMessage(message).setCancelable(false)
        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
          }
        });
    AlertDialog alert = builder.create();
    alert.show();
  }

  private class MyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      logger.log(Level.INFO, "Received intent " + action);

      if (action.equalsIgnoreCase("edu.ncsu.csc450.intelligentalarm.KILL")) {
        finish();
      } else if (action.equalsIgnoreCase("edu.ncsu.csc450.intelligentalarm.WEATHER_UPDATE")) {
        boolean isAdverse = intent.getBooleanExtra("isAdverse", false);
        logger.log(Level.INFO, "Is weather adverse: " + isAdverse);
        if (isAdverse) {
          Calendar calendar = Calendar.getInstance();
          long curTimeInMillis = calendar.getTimeInMillis();

          calendar.set(Calendar.YEAR, alarmYear);
          calendar.set(Calendar.MONTH, alarmMonth);
          calendar.set(Calendar.DAY_OF_MONTH, alarmDayOfMonth);
          calendar.set(Calendar.HOUR_OF_DAY, alarmHourOfDay);
          calendar.set(Calendar.MINUTE, alarmMinute);
          long alarmTimeInMillis = calendar.getTimeInMillis();

          long alarmResetTimeInMillis = alarmTimeInMillis - (30 * 60 * 1000);
          if (alarmResetTimeInMillis < curTimeInMillis) {
            alarmResetTimeInMillis = curTimeInMillis;
          }

          Calendar alarmCalendar = Calendar.getInstance();
          alarmCalendar.setTimeInMillis(alarmResetTimeInMillis);
          setAlarm("Due to adverse weather alarm advanced to ", alarmCalendar, true);
        }
      } else if (action.equalsIgnoreCase("edu.ncsu.csc450.intelligentalarm.FLIGHT_SCHEDULE")) {
          int delayTime= intent.getIntExtra("delayTime", 0);
          logger.log(Level.INFO, "Flight Delay Time: " + delayTime);
    	  Calendar calendar = Calendar.getInstance();

          calendar.set(Calendar.YEAR, alarmYear);
          calendar.set(Calendar.MONTH, alarmMonth);
          calendar.set(Calendar.DAY_OF_MONTH, alarmDayOfMonth);
          calendar.set(Calendar.HOUR_OF_DAY, alarmHourOfDay);
          calendar.set(Calendar.MINUTE, alarmMinute);
          long alarmTimeInMillis = calendar.getTimeInMillis();
          
          if (delayTime == 30) {

            long alarmResetTimeInMillis = alarmTimeInMillis + (30 * 60 * 1000);
//            if (alarmResetTimeInMillis < curTimeInMillis) {
//              alarmResetTimeInMillis = curTimeInMillis;
//            }

            Calendar alarmCalendar = Calendar.getInstance();
            alarmCalendar.setTimeInMillis(alarmResetTimeInMillis);
            setAlarm("Flight delayed by 30 mins. Alarm set to  ", alarmCalendar, true);
          } else if(delayTime == 60) {
        	  
            long alarmResetTimeInMillis = alarmTimeInMillis + (60 * 60 * 1000);

            Calendar alarmCalendar = Calendar.getInstance();
            alarmCalendar.setTimeInMillis(alarmResetTimeInMillis);
            setAlarm("Flight delayed by 60 mins. Alarm set to  ", alarmCalendar, true);
          } else {

            long alarmResetTimeInMillis = alarmTimeInMillis;

            Calendar alarmCalendar = Calendar.getInstance();
            alarmCalendar.setTimeInMillis(alarmResetTimeInMillis);
            setAlarm("Flight delayed by 0 mins. Alarm set to  ", alarmCalendar, true);
          }
        } else if (action.equalsIgnoreCase("edu.ncsu.csc450.intelligentalarm.FRIEND_UPDATE")) {
        String friendName = intent.getStringExtra("friendName");
        logger.log(Level.INFO, "Friend contacted: " + friendName);
        Toast.makeText(AlarmActivity.this, "Due to repeated snoozing, I contacted: " + friendName
            + " to wake you up", Toast.LENGTH_LONG).show();
      }
    }
  }

  private static class MyHandler extends Handler {
    private final WeakReference<AlarmActivity> mainActivity;

    public MyHandler(AlarmActivity mainActivity) {
      this.mainActivity = new WeakReference<AlarmActivity>(mainActivity);
    }

    @Override
    public void handleMessage(Message msg) {
      Bundle bundle = msg.getData();
      if (bundle.containsKey("error")) {
        String message = bundle.getString("error");
        mainActivity.get().ShowDialog(message);
      }
    }

    public void postError(String error) {
      Message msg = obtainMessage();
      Bundle b = new Bundle();
      b.putString("error", error);
      msg.setData(b);
      sendMessage(msg);
    }
  }

  private class MyLocationListener implements LocationListener {
    @Override
    public void onLocationChanged(Location location) {
      userLatitude = location.getLatitude();
      userLongitude = location.getLongitude();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
      // No action
    }

    @Override
    public void onProviderEnabled(String provider) {
      // No action
    }

    @Override
    public void onProviderDisabled(String provider) {
      // No action
    }
  }
}
