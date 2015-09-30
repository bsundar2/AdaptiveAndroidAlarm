package edu.ncsu.csc450.notifsystem;

import edu.ncsu.csc450.intelligentalarm.onto.AlarmOntology;
import edu.ncsu.csc450.intelligentalarm.onto.Weather;
import jade.content.ContentManager;
import jade.content.abs.AbsAggregate;
import jade.content.abs.AbsConcept;
import jade.content.abs.AbsPredicate;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.BasicOntology;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import jade.util.leap.Iterator;
import jade.util.leap.Set;
import jade.util.leap.SortedSetImpl;

public class WeatherNotificationAgent extends Agent {

  private static final long serialVersionUID = -7812648301750855180L;

  private Logger logger = Logger.getMyLogger(this.getClass().getName());

  private static final String ALARM_MANAGER_NAME = "alarm-manager";
  private static final String WEATHER_ID = "__weather__";

  private Set participants = new SortedSetImpl();

  private Codec codec = new SLCodec();
  private Ontology onto = AlarmOntology.getInstance();

  protected void setup() {
    ContentManager cm = getContentManager();
    cm.registerLanguage(codec);
    cm.registerOntology(onto);
    cm.setValidationMode(false);
    logger.info("WeatherNotificationAgent.setup()");

    addBehaviour(new ParticipantsManager(this));
    addBehaviour(new NotificationRequestListener(this));
  }

  protected void takeDown() {
    logger.info("WeatherNotificationAgent.takeDown()");
  }

  class ParticipantsManager extends CyclicBehaviour {
    private static final long serialVersionUID = -5082763004871104161L;
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
            AbsPredicate p = (AbsPredicate) myAgent.getContentManager().extractAbsContent(msg);
            if (p.getTypeName().equals(AlarmOntology.JOINED)) {
              AbsAggregate agg = (AbsAggregate) p.getAbsTerm(AlarmOntology.JOINED_WHO);
              if (agg != null) {
                Iterator it = agg.iterator();
                while (it.hasNext()) {
                  AbsConcept c = (AbsConcept) it.next();
                  participants.add(BasicOntology.getInstance().toObject(c));
                }
              }
              logger.info("Added a participant");
            }
            if (p.getTypeName().equals(AlarmOntology.LEFT)) {
              AbsAggregate agg = (AbsAggregate) p.getAbsTerm(AlarmOntology.JOINED_WHO);
              if (agg != null) {
                Iterator it = agg.iterator();
                while (it.hasNext()) {
                  AbsConcept c = (AbsConcept) it.next();
                  participants.remove(BasicOntology.getInstance().toObject(c));
                }
              }
              logger.info("Removed a participant");
            }
          } catch (CodecException e) {
            Logger.println(e.toString());
            e.printStackTrace();
          } catch (OntologyException e) {
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

  class NotificationRequestListener extends CyclicBehaviour {
    private static final long serialVersionUID = -608978500842345487L;
    private MessageTemplate template = MessageTemplate.MatchConversationId(WEATHER_ID);

    NotificationRequestListener(Agent a) {
      super(a);
    }

    public void action() {
      try {
        ACLMessage msg = myAgent.receive(template);
        if (msg != null) {
          if (msg.getPerformative() == ACLMessage.REQUEST) {
            ACLMessage notif1 = msg.createReply();
            notif1.setPerformative(ACLMessage.INFORM);
            notif1.setConversationId(msg.getSender().getLocalName());
            Weather weather = new Weather();
            weather.setIs_weather_adverse(isWeatherAdverse());
            getContentManager().fillContent(notif1, weather);
            myAgent.send(notif1);
          } else {
            handleUnexpected(msg);
          }
        } else {
          block();
        }
      } catch (CodecException | OntologyException e) {
        logger.severe("Can't send weather information");
        e.printStackTrace();
      }
    }
  }

  private void handleUnexpected(ACLMessage msg) {
    if (logger.isLoggable(Logger.WARNING)) {
      logger.log(Logger.WARNING, "Unexpected message received from " + msg.getSender().getName());
      logger.log(Logger.WARNING, "Content is: " + msg.getContent());
    }
  }

  // Mock method
  private boolean isWeatherAdverse() {
    boolean[] candidateWeatherAdversity = { true, false };
    int randNum = (int) (Math.random() * 2);
    return candidateWeatherAdversity[randNum];
  }
}
