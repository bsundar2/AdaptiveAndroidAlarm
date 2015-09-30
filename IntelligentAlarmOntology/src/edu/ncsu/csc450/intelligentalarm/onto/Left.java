package edu.ncsu.csc450.intelligentalarm.onto;

import java.util.List;

import jade.content.Predicate;
import jade.core.AID;

public class Left implements Predicate {

  private static final long serialVersionUID = 3983113218202015792L;

  private List<AID> _who;

  public void setWho(List<AID> who) {
    _who = who;
  }

  public List<AID> getWho() {
    return _who;
  }
}
