package edu.ncsu.csc450.intelligentalarm.onto;

import java.util.List;

import jade.content.Predicate;
import jade.core.AID;

public class Joined implements Predicate {

  private static final long serialVersionUID = -4503163497660036704L;
  
  private List<AID> _who;

	public void setWho(List<AID> who) {
		_who = who;
	}

	public List<AID> getWho() {
		return _who;
	}

}
