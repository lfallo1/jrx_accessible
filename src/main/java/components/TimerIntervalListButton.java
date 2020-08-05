/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;
import com.cozcompany.jrx.accessibility.SwrIndicator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

/**
 * This class contains the controller update interval control and the associated
 * Timer and its methods.
 * 
 * @author Coz
 */
public class TimerIntervalListButton extends RWListButton {
    public Map<String, Integer> intervals = null;
    final Integer INITIAL_INDEX = 5;
    Timer periodicTimer;
    JRX_TX parent;
    
    public TimerIntervalListButton(JRX_TX aParent) {
        super(aParent, "", "", "TIMER INTERVAL", "CHOOSE A TIMER INTERVAL");
        super.numericMode = true;
        parent= aParent;
    }
    
    /**
     * Initialize the sv_timerIntervalListButton with Paul's ingenius algorithm 
     * to make steps { 10ms,20ms,50ms,100ms,.....5s,10s,50s,OFF }.
     * 
     */
    public void initTimeValues() {
        super.initialize();
        removeAllItems();
        intervals = new TreeMap<>();
        double[] msteps = new double[]{1, 2, 5};
        String sl;
        double bv = 10;
        for (int p = 0; p <= 3; p++) {
            for (double lv : msteps) {
                double v = bv * lv;
                if (v >= 1000) {
                    sl = String.format("%d s", (int) (v / 1000));
                } else {
                    sl = String.format("%d ms", (int) v);
                }
                intervals.put(sl, (int) v);
                addListItem(sl, v, "" + v);
            }
            bv *= 10;
        }
        addListItem("Off", 0, "0");
        setIndex(INITIAL_INDEX);
        super.choices = getChoiceStrings();
        super.dialog.setNewData(choices);
        
    }
    
    public double getTimeInterval() {
        return intervals.get(getSelectedItem());
    }
    
    public void startCyclicalTimer() {
        if (parent.comArgs.runTimer()) {
            if (periodicTimer != null) {
                periodicTimer.cancel();
            }
            // This is not a java swing Timer.
            periodicTimer = new java.util.Timer();
            resetTimer();
        }
    }
    public void setIndex(int index) {
        index = Math.max(0, index);
        index = Math.min(index, intervals.size() - 1);
        setSelectedIndex(index);  // Sets button text.
    }
        
    /**
     * Get a delay interval from the sv_timerIntervalComboBox and schedule a
     * TimerTask (PeriodicEvents) after the given interval; technically this
     * method RESTARTS the timer (as opposed to resetting the timer).
     */
    public void resetTimer() {
        long delay = (long) getConvertedValue();
        //p("delay: " + delay);
        if (delay > 0 && periodicTimer != null) {
            periodicTimer.schedule(new PeriodicEvents(), delay);
        }
    }
    
    /**
     * cancelTimer method is used when a connection is closed and the timer is
     * no longer required.
     */
    public void cancelTimer() {
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
        }
    }
    /**
     * This is an internal variable.  Do not write to radio, ever.
     * @param force 
     */
    @Override
    public void writeValue(boolean force) {
        // do nothing.
    }
    /**
     * This method is called for every control during initialize()... on startup\
     * and whenever the radio or interface is changed.  This is an internal
     * variable and is never read from the radio.
     * @param all 
     */
    @Override
    public void selectiveReadValue(boolean all) {
        // Do nothing.
    }

    /**
     * The control loop for dynamic behavior.
     */
    public class PeriodicEvents extends TimerTask {

        @Override
        public void run() {
            if (!parent.getScopePanel().isRunning() && parent.scanStateMachine.getScanTimer() == null) {                
                if (parent.getSignalStrength()) {
                    // Signal Strength is supported and unsuccessful.
                    parent.setSMeter();
                    parent.squelchScheme.getSquelch();
                }
                parent.setComErrorIcon();               
                parent.readRadioControls(false);
                ((SwrIndicator)parent.swrIndicator).updateSwr();
            }
            if (parent.slowRadio || parent.scanStateMachine.getScanTimer() != null) {
//                timerUpdateFreq();
//                timerSetSliders();  //@TODO Coz fix this.
            }
            resetTimer();
        }
    }

}
