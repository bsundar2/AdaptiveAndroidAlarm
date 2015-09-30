package edu.ncsu.csc450.notifsystem;

import jade.MicroBoot;
import jade.core.MicroRuntime;

public class Start extends MicroBoot {
  public static void main(String args[]) {
    MicroBoot.main(args);
    try {
            
      MicroRuntime.startAgent("N-Weather",
          "edu.ncsu.csc450.notifsystem.WeatherNotificationAgent", null);
      
      MicroRuntime.startAgent("N-Flight",
              "edu.ncsu.csc450.notifsystem.FlightNotificationAgent", null);
      
      MicroRuntime.startAgent("U-Bob",
          "edu.ncsu.csc450.notifsystem.UserAgent", null);
      
      MicroRuntime.startAgent("U-Charlie",
          "edu.ncsu.csc450.notifsystem.UserAgent", null);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
