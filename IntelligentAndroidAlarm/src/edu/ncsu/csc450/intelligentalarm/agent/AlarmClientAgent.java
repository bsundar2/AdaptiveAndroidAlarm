package edu.ncsu.csc450.intelligentalarm.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import edu.ncsu.csc450.intelligentalarm.onto.AlarmOntology;
import edu.ncsu.csc450.intelligentalarm.onto.FlightSchedule;
import edu.ncsu.csc450.intelligentalarm.onto.Joined;
import edu.ncsu.csc450.intelligentalarm.onto.Left;
import edu.ncsu.csc450.intelligentalarm.onto.UserLocation;
import edu.ncsu.csc450.intelligentalarm.onto.Weather;
import android.content.Context;
import android.content.Intent;
import jade.content.ContentManager;
import jade.content.Predicate;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import jade.util.leap.Iterator;
import jade.util.leap.Set;
import jade.util.leap.SortedSetImpl;

public class AlarmClientAgent extends Agent implements AlarmClientInterface {

  private static final long serialVersionUID = -8395949932309394954L;

  private Logger logger = Logger.getJADELogger(this.getClass().getName());

  private static final String ALARM_MANAGER_NAME = "alarm-manager";
  private static final String WEATHER_ID = "__weather__";
  private static final String FLIGHT_ID = "__flight__";
  private static final String USER_ID = "__user__";

  private Codec codec = new SLCodec();
  private Ontology onto = AlarmOntology.getInstance();

  private Context context;

  private Set participants = new SortedSetImpl();

  private double userLatitude = 35.771888;
  private double userLongitude = -78.673573;

  @Override
  protected void setup() {
    Object[] args = getArguments();
    if (args != null && args.length > 0) {
      if (args[0] instanceof Context) {
        context = (Context) args[0];
      }
    }

    ContentManager cm = getContentManager();
    cm.registerLanguage(codec);
    cm.registerOntology(onto);
    cm.setValidationMode(false);

    registerO2AInterface(AlarmClientInterface.class, this);

    addBehaviour(new ParticipantsManager(this));
  }

  @Override
  protected void takeDown() {
    // TODO
  }

  class ParticipantsManager extends CyclicBehaviour {
    private static final long serialVersionUID = -1710608365295588131L;
    private MessageTemplate template;

    ParticipantsManager(Agent a) {
      super(a);
    }

    public void onStart() {
      ACLMessage subscription = new ACLMessage(ACLMessage.SUBSCRIBE);
      subscription.setLanguage(codec.getName());
      subscription.setOntology(onto.getName());
      String convId = myAgent.getLocalName();
      subscription.setConversationId(convId);
      subscription.addReceiver(new AID(ALARM_MANAGER_NAME, AID.ISLOCALNAME));
      myAgent.send(subscription);
      template = MessageTemplate.MatchConversationId(convId);
    }

    public void action() {
      ACLMessage msg = myAgent.receive(template);
      if (msg != null) {
        if (msg.getPerformative() == ACLMessage.INFORM) {
          try {
            Predicate p = (Predicate) myAgent.getContentManager().extractContent(msg);
            if (p instanceof Joined) {
              Joined joined = (Joined) p;
              List<AID> aid = (List<AID>) joined.getWho();
              for (AID a : aid)
                participants.add(a);
              notifyParticipantsChanged();
            } else if (p instanceof Left) {
              Left left = (Left) p;
              List<AID> aid = (List<AID>) left.getWho();
              for (AID a : aid)
                participants.remove(a);
              notifyParticipantsChanged();
            } else if (p instanceof Weather) {
              Weather weather = (Weather) p;
              boolean isAdverse = weather.getIs_weather_adverse();
              notifyWeather(isAdverse);
            } else if (p instanceof FlightSchedule) {
            	FlightSchedule flight = (FlightSchedule) p;
            	int delayTime = flight.getDelay_duration();
            	notifyFlightDelay(delayTime);
            } else if (p instanceof UserLocation) {
              UserLocation friendLoc = (UserLocation) p;
              String[] locParts = friendLoc.getLocation().split(",");
              double distance = getDistance(userLatitude, userLongitude,
                  Double.parseDouble(locParts[0]), Double.parseDouble(locParts[1]));
              if (distance < 1000.0) {
                String friendName = msg.getSender().getLocalName();
                notifyUserAboutAFriendNearby(friendName);
              }
            }
          } catch (Exception e) {
            Logger.println(e.toString());
            e.printStackTrace();
          }
        } else {
          handleUnexpected(msg);
        }
      } else {
        block();
      }
    }
  }

  private class WeatherRequester extends OneShotBehaviour {
    private static final long serialVersionUID = -5413800109481177996L;

    private WeatherRequester(Agent a) {
      super(a);
    }

    public void action() {
      AID weatherAid = null;
      Iterator it = participants.iterator();
      while (it.hasNext()) {
        AID id = (AID) it.next();
        if (id.getName().startsWith("N-Weather")) {
          weatherAid = id;
          break;
        }
      }
      if (weatherAid != null) {
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.setLanguage(codec.getName());
        request.setOntology(onto.getName());
        request.setConversationId(WEATHER_ID);
        request.addReceiver(weatherAid);
        myAgent.send(request);
      } else {
        logger.log(Level.WARNING, "There is no weather agent.");
      }
    }
  }
  
  private class DelayTimeRequester extends OneShotBehaviour {
	    private static final long serialVersionUID = -5413800109481177396L;

	    private DelayTimeRequester(Agent a) {
	      super(a);
	    }

	    public void action() {
	      AID delayAid = null;
	      Iterator it = participants.iterator();
	      while (it.hasNext()) {
	        AID id = (AID) it.next();
	        if (id.getName().startsWith("N-Flight")) {
	          delayAid = id;
	          logger.log(Level.INFO, "Agent name starts with N-Flight");
	          break;
	        }
	      }
	      if (delayAid != null) {
	        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
	        request.setLanguage(codec.getName());
	        request.setOntology(onto.getName());
	        request.setConversationId(FLIGHT_ID);
	        request.addReceiver(delayAid);
	        myAgent.send(request);
	      } else {
	        logger.log(Level.WARNING, "There is no flight agent.");
	      }
	    }
	  }
  
  

  private class FriendLocationRequester extends OneShotBehaviour {
    private static final long serialVersionUID = 3919333461532307027L;

    private FriendLocationRequester(Agent a) {
      super(a);
    }

    public void action() {
      Iterator it = participants.iterator();
      while (it.hasNext()) {
        AID id = (AID) it.next();
        if (id.getName().startsWith("U-") && id.getLocalName() != myAgent.getLocalName()) {
          ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
          request.setLanguage(codec.getName());
          request.setOntology(onto.getName());
          request.setConversationId(USER_ID);
          request.addReceiver(id);
          myAgent.send(request);
        }
      }
    }
  }

  private void notifyParticipantsChanged() {
    Intent broadcast = new Intent();
    broadcast.setAction("edu.ncsu.csc450.intelligentalarm.REFRESH_PARTICIPANTS");
    logger.log(Level.INFO, "Sending broadcast " + broadcast.getAction());
    context.sendBroadcast(broadcast);
  }

  private void notifyWeather(boolean isAdverse) {
    Intent broadcast = new Intent();
    broadcast.setAction("edu.ncsu.csc450.intelligentalarm.WEATHER_UPDATE");
    broadcast.putExtra("isAdverse", isAdverse);
    logger.log(Level.INFO, "Sending broadcast " + broadcast.getAction());
    context.sendBroadcast(broadcast);
  }
  
  private void notifyFlightDelay(int delayTime) {
	  Intent broadcast = new Intent();
	  broadcast.setAction("edu.ncsu.csc450.intelligentalarm.FLIGHT_SCHEDULE");
	  broadcast.putExtra("delayTime", delayTime); 
	  logger.log(Level.INFO, "Sending broadcast " + broadcast.getAction());
	  context.sendBroadcast(broadcast);
  }

  private void notifyUserAboutAFriendNearby(String friendName) {
    Intent broadcast = new Intent();
    broadcast.setAction("edu.ncsu.csc450.intelligentalarm.FRIEND_UPDATE");
    broadcast.putExtra("friendName", friendName);
    logger.log(Level.INFO, "Sending broadcast " + broadcast.getAction());
    context.sendBroadcast(broadcast);
  }

  private double getDistance(double lat1, double lon1, double lat2, double lon2) {
    double earthRadius = 6371000; // meters
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
        * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    double dist = earthRadius * c;

    return dist;
  }

  private void handleUnexpected(ACLMessage msg) {
    if (logger.isLoggable(Logger.WARNING)) {
      logger.log(Logger.WARNING, "Unexpected message received from " + msg.getSender().getName());
      logger.log(Logger.WARNING, "Content is: " + msg.getContent());
    }
  }

  @Override
  public List<String> getParticipantNames() {
    List<String> pp = new ArrayList<String>();
    Iterator it = participants.iterator();
    while (it.hasNext()) {
      AID id = (AID) it.next();
      pp.add(id.getLocalName());
    }
    return pp;
  }

  @Override
  public List<String> getNotifierNames() {
    List<String> pp = new ArrayList<String>();
    Iterator it = participants.iterator();
    while (it.hasNext()) {
      AID id = (AID) it.next();
      if (id.getName().startsWith("N-")) {
        pp.add(id.getLocalName());
      }
    }
    return pp;
  }

  @Override
  public List<String> getUserNames() {
    List<String> pp = new ArrayList<String>();
    Iterator it = participants.iterator();
    while (it.hasNext()) {
      AID id = (AID) it.next();
      if (id.getName().startsWith("U-")) {
        pp.add(id.getLocalName());
      }
    }
    return pp;
  }

  @Override
  public void getWeatherUpdate() {
    addBehaviour(new WeatherRequester(this));
  }

  @Override
  public void getDelayTimeUpdate() {
	  addBehaviour(new DelayTimeRequester(this));
  }
  
  @Override
  public void findAFriendNearby(double _userLatitude, double _userLongitude) {
    userLatitude = _userLatitude;
    userLongitude = _userLongitude;
    addBehaviour(new FriendLocationRequester(this));
  }
}
