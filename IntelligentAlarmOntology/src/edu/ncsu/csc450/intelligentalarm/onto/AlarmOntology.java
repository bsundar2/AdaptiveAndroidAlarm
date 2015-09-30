package edu.ncsu.csc450.intelligentalarm.onto;

import jade.content.onto.BasicOntology;
import jade.content.onto.CFReflectiveIntrospector;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.schema.ConceptSchema;
import jade.content.schema.ObjectSchema;
import jade.content.schema.PredicateSchema;
import jade.content.schema.PrimitiveSchema;

public class AlarmOntology extends Ontology implements AlarmVocabulary {

  private static final long serialVersionUID = -1678423675684135329L;

  private static final Ontology theInstance = new AlarmOntology();

  public static Ontology getInstance() {
    return theInstance;
  }

  private AlarmOntology() {
    super(ONTOLOGY_NAME, BasicOntology.getInstance(), new CFReflectiveIntrospector());

    try {
      add(new PredicateSchema(JOINED), Joined.class);
      add(new PredicateSchema(LEFT), Left.class);
      
      add(new PredicateSchema(WEATHER), Weather.class);
      add(new PredicateSchema(FLIGHT_SCHEDULE), FlightSchedule.class);
      
      add(new PredicateSchema(USER_LOCATION), UserLocation.class);

      PredicateSchema ps = (PredicateSchema) getSchema(JOINED);
      ps.add(JOINED_WHO, (ConceptSchema) getSchema(BasicOntology.AID), 1, ObjectSchema.UNLIMITED);

      ps = (PredicateSchema) getSchema(LEFT);
      ps.add(LEFT_WHO, (ConceptSchema) getSchema(BasicOntology.AID), 1, ObjectSchema.UNLIMITED);
          
      ps = (PredicateSchema) getSchema(WEATHER);
      ps.add(WEATHER_IS_ADVERSE, (PrimitiveSchema) getSchema(BasicOntology.BOOLEAN));
      
      ps = (PredicateSchema) getSchema(USER_LOCATION);
      ps.add(LOCATION, (PrimitiveSchema) getSchema(BasicOntology.STRING));
    } catch (OntologyException oe) {
      oe.printStackTrace();
    }
  }
}
