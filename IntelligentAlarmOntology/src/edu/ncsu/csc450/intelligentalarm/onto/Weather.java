package edu.ncsu.csc450.intelligentalarm.onto;

import jade.content.Predicate;

public class Weather implements Predicate {

  private static final long serialVersionUID = 2974077956135770175L;
  
  private boolean _isAdverse;

  public boolean getIs_weather_adverse() {
    return _isAdverse;
  }

  public void setIs_weather_adverse(boolean isAdverse) {
    _isAdverse = isAdverse;
  }
}