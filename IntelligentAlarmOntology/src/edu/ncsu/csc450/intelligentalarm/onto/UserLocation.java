package edu.ncsu.csc450.intelligentalarm.onto;

import jade.content.Predicate;

public class UserLocation implements Predicate {

  private static final long serialVersionUID = 3701074366317793546L;

  private String _location;

  public String getLocation() {
    return _location;
  }

  public void setLocation(String location) {
    _location = location;
  }
}
