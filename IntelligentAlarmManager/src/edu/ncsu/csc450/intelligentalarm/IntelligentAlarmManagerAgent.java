package edu.ncsu.csc450.intelligentalarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import edu.ncsu.csc450.intelligentalarm.onto.AlarmOntology;
import edu.ncsu.csc450.intelligentalarm.onto.Joined;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.core.AID;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.introspection.AMSSubscriber;
import jade.domain.introspection.DeadAgent;
import jade.domain.introspection.Event;
import jade.domain.introspection.IntrospectionOntology;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SubscriptionResponder;
import jade.proto.SubscriptionResponder.Subscription;
import jade.proto.SubscriptionResponder.SubscriptionManager;
import jade.util.Logger;

public class IntelligentAlarmManagerAgent extends Agent implements SubscriptionManager {

  private static final long serialVersionUID = -8361538831234468188L;

  private Logger logger = Logger.getMyLogger(this.getClass().getName());

  private Map<AID, Subscription> participants = new HashMap<AID, Subscription>();

  private Codec codec = new SLCodec();
  private Ontology onto = AlarmOntology.getInstance();
  private AMSSubscriber myAMSSubscriber;

  @Override
  protected void setup() {
    getContentManager().registerLanguage(codec);
    getContentManager().registerOntology(onto);

    MessageTemplate sTemplate = MessageTemplate.and(
        MessageTemplate.MatchPerformative(ACLMessage.SUBSCRIBE),
        MessageTemplate.and(MessageTemplate.MatchLanguage(codec.getName()),
            MessageTemplate.MatchOntology(onto.getName())));
    addBehaviour(new SubscriptionResponder(this, sTemplate, this));

    myAMSSubscriber = new AMSSubscriber() {
      private static final long serialVersionUID = 6932099071626107779L;

      @SuppressWarnings("unchecked")
      protected void installHandlers(@SuppressWarnings("rawtypes") Map handlersTable) {
        handlersTable.put(IntrospectionOntology.DEADAGENT, new EventHandler() {
          private static final long serialVersionUID = -4443971246669421780L;

          public void handle(Event ev) {
            DeadAgent da = (DeadAgent) ev;
            AID id = da.getAgent();
            logger.log(Level.INFO, "Agent " + id.getName() + " died.");
          }
        });
      }
    };
    addBehaviour(myAMSSubscriber);
  }

  @Override
  protected void takeDown() {
    send(myAMSSubscriber.getCancel());
  }

  @Override
  public boolean register(Subscription s) throws RefuseException, NotUnderstoodException {
    try {
      AID newId = s.getMessage().getSender();
      logger.log(Level.INFO, "New participant: " + newId.getName());

      if (!participants.isEmpty()) {
        ACLMessage notif1 = s.getMessage().createReply();
        notif1.setPerformative(ACLMessage.INFORM);

        ACLMessage notif2 = (ACLMessage) notif1.clone();
        notif2.clearAllReceiver();
        Joined joined = new Joined();
        List<AID> who = new ArrayList<AID>(1);
        who.add(newId);
        joined.setWho(who);
        getContentManager().fillContent(notif2, joined);

        who.clear();
        Iterator<AID> it = participants.keySet().iterator();
        while (it.hasNext()) {
          AID oldId = it.next();
          Subscription oldUserS = (Subscription) participants.get(oldId);
          oldUserS.notify(notif2);
          who.add(oldId);
        }

        getContentManager().fillContent(notif1, joined);
        s.notify(notif1);
      }
      participants.put(newId, s);

    } catch (CodecException | OntologyException e) {
      e.printStackTrace();
      throw new RefuseException("Subscription error");
    }
    return false;
  }

  @Override
  public boolean deregister(Subscription s) throws FailureException {
    AID oldId = s.getMessage().getSender();
    logger.log(Level.INFO, "Participant leaving: " + oldId.getName());
    return false;
  }
}
