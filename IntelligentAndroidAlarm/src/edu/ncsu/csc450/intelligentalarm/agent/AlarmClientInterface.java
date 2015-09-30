package edu.ncsu.csc450.intelligentalarm.agent;

import java.util.List;

public interface AlarmClientInterface {
  public List<String> getParticipantNames();
  public List<String> getNotifierNames();
  public List<String> getUserNames();
  public void getWeatherUpdate();
  public void getDelayTimeUpdate();
  public void findAFriendNearby(double userLatitude, double userLongitude);
}
