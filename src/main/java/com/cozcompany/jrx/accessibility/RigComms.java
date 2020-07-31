/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;
import java.util.List;
import java.util.ArrayList;

/**
 * Class to keep rig communications state and alert observers about state 
 * change.
 * "online" -  means that we are able to talk to a real radio or we are talking to
 * a HAMLIB dummy radio.
 * 
 * "offline" - means that we are unable to talk to a real radio.
 * 
 * @author Coz
 */
public class RigComms {
    private boolean online = false;
    public interface CommsObserver {
        void update(String event);
    } 
    private final List<CommsObserver> observers = new ArrayList<>();
  
    private void notifyObservers(String event) {
        //alternative lambda expression: observers.forEach(Observer::update);
        observers.forEach(observer -> observer.update(event)); 
    }
  
    public void addObserver(CommsObserver observer) {
        observers.add(observer);
    }

    public void setOffline() {
        this.online = false;
        notifyObservers("offline");       
    }

    public void setOnline() {
        this.online = true;
        notifyObservers("online");       
    }
}

