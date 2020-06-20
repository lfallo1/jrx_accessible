/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import java.awt.Color;
import java.awt.Container;
import java.text.DecimalFormat;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;

/**
 * This class is a re-entrant interface to the radio VFO's using locks to give 
 * results to any thread as to the selected VFO and its frequency and is 
 * the interface to change the selected JRadioButtonMenuItem state and
 * the transmit / receive VFO via software.
 * 
 * The actual state machine is in the JRadioButtonGroup which enforces selection
 * of only one of the group's button at a time.
 * 
 * @author Coz
 */
public class VfoSelectionInterface {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();    
    JRadioButtonMenuItem vfoA;
    JRadioButtonMenuItem vfoB;
    JTextField frequencyVfoA;
    JTextField frequencyVfoB;
    static Color SELECTED_COLOR = Color.WHITE; 
    static Color UNSELECTED_COLOR = Color.LIGHT_GRAY;
    JFrame aFrame;
 
    public VfoSelectionInterface(JRadioButtonMenuItem a, JRadioButtonMenuItem b, 
            JTextField freqA, JTextField freqB) {
        
        frequencyVfoA = freqA;
        frequencyVfoB = freqB;
        vfoA =  a;
        vfoB =  b;
        // Make the vfo item selection exclusive.
        Container container = a.getParent();
        ButtonGroup group = new ButtonGroup();
        group.add(a);
        group.add(b);
        //Menu items must be in the same group to be exclusively selected.
        //assert (vfoA.getModel().getGroup()==vfoB.getModel().getGroup());
    }
    
    public long getVfoAFrequency() {
        long frequencyHertz = 0;
        try {
            lock.readLock().lock();  //blocks until lock is available.            
            String valString;
            long v = 0;
            //Read freq from Radio VFO A.
            try {
                // get current vfo
                // set current vfo to A
                // Read Vfo A
                String sf = aFrame.sendRadioCom("f", 0, false);
                frequencyHertz = Long.parseLong(sf);
                // Set current vfo to what is was.
                
            } catch (Exception e) {
            }                  
            double freqMhz = ((double)v )/ 1.E06;
            valString = Double.toString(v);
            frequencyVfoA.setText(valString); 
        }
        finally {
            lock.readLock().unlock(); 
        }
        return frequencyHertz;
    }
    
    public long getVfoBFrequency() {
        long frequencyHertz = 0;
        try {
            lock.readLock().lock();  //blocks until lock is available.               
            String valString;      
            //Simlate read freq from Radio VFO B.
            valString = frequencyVfoB.getText();        
            double freqMhz = Double.valueOf(valString);
            frequencyHertz = (long) (freqMhz * 1.E06) ; 
        }
        finally {
            lock.readLock().unlock(); 
        }
        return frequencyHertz;
    }

    public long getSelectedVfoFrequency() {
        long frequencyHertz = 0;
        try {
            lock.readLock().lock();  //blocks until lock is available.
            String valString;
            //Read freq from Radio.
            if (vfoA.isSelected()) {
                // Read frequency from Radio VFO A.
                valString = frequencyVfoA.getText();
            } else {
                // Read frequency from Radio VFO B.
                valString = frequencyVfoB.getText();
            }
            double freqMhz = Double.valueOf(valString);
            frequencyHertz = (long) (freqMhz * 1.E06) ;
        }
        finally {
            lock.readLock().unlock(); 
        }
        return frequencyHertz;
    }
    
    public boolean vfoA_IsSelected() {
        boolean isVfoA = true;
        lock.readLock().lock();  //blocks until lock is available.
        //Simlate read selection from Radio.       
        isVfoA = ( vfoA.isSelected() );
        
        lock.readLock().unlock();  
        return isVfoA;
    }    
 
    public boolean setVfoASelected(){
        boolean success = true;  // Simulation is always successful.
        boolean isSelectedA = vfoA_IsSelected();
        //if (isSelectedA) return success; // VFO B is already selected.
        
        lock.writeLock().lock();  //blocks until lock is available.
        System.out.println("obtained lock. Vfo A is selected :" + isSelectedA);
        vfoA.setSelected(true);
        frequencyVfoA.setBackground(SELECTED_COLOR);
        frequencyVfoB.setBackground(UNSELECTED_COLOR);
        lock.writeLock().unlock();            
        
        isSelectedA = vfoA_IsSelected();
        System.out.println("Released the lock. Vfo A is selected :"+ isSelectedA);
        return success;
    }

    public boolean setVfoBSelected(){
        boolean success = true;  // Simulation is always successful. 
        boolean isSelectedA = vfoA_IsSelected();
        //if(!isSelectedA) return success; // VFO B is already selected.
        
        lock.writeLock().lock();  //blocks until lock is available.       
        System.out.println("obtained lock. Vfo A is selected :" + isSelectedA);
        vfoB.setSelected(true);
        frequencyVfoB.setBackground(SELECTED_COLOR);
        frequencyVfoA.setBackground(UNSELECTED_COLOR);
        lock.writeLock().unlock();            
        
        isSelectedA = vfoA_IsSelected();
        System.out.println("Released the lock. Vfo A is selected :"+ isSelectedA);
        return success;
    }
    
    /**
     * Write the given frequency to the currently selected radio VFO.
     * @return true when frequency successfully communicated to radio.
     * @param frequencyHertz
    */
    public boolean writeFrequencyToRadioSelectedVfo(long frequencyHertz) {
        boolean success = true;
        boolean isVfoA = vfoA_IsSelected(); // under readLock        
        lock.writeLock().lock();  //blocks until lock is available.
    
        // Simulate sending freq value to Radio for testing.
        // Update the radio frequency text field.
        double mhz = (double) frequencyHertz;
        mhz = mhz / 1000000.0;
        DecimalFormat decimalFormat = new DecimalFormat("#.000000");
        String numberAsString = decimalFormat.format(mhz);
        if (isVfoA)
            frequencyVfoA.setText(numberAsString);
        else
            frequencyVfoB.setText(numberAsString);

        lock.writeLock().unlock();
        return success;
    }
     
    /**
     * Given which Vfo to access, write the given frequency to the radio.
     * @return true when frequency successfully communicated to radio.
     * @param frequencyHertz 
     * @param isVfoA
     * 
    */
    public boolean writeFrequencyToRadio(long frequencyHertz, boolean isVfoA) {
        boolean success = true;
        lock.writeLock().lock();  //blocks until lock is available.
    
        // Simulate sending freq value to Radio for testing.
        // Update the radio frequency text field.
        double mhz = (double) frequencyHertz;
        mhz = mhz / 1000000.0;
        DecimalFormat decimalFormat = new DecimalFormat("#.000000");
        String numberAsString = decimalFormat.format(mhz);
        if (isVfoA)
            frequencyVfoA.setText(numberAsString);
        else
            frequencyVfoB.setText(numberAsString);

        lock.writeLock().unlock();
        return success;
    }

    public boolean writeFrequencyToRadioVfoA(long frequencyHertz) {
        return writeFrequencyToRadio(frequencyHertz, true);
    }

    public boolean writeFrequencyToRadioVfoB(long frequencyHertz) {
        return writeFrequencyToRadio(frequencyHertz, false);
    }
    
    public boolean copyAtoB() {
        boolean success = true;
        long freqA = getVfoAFrequency();
        success = writeFrequencyToRadioVfoB(freqA);       
        return success;       
    }
    
    public boolean swapAwithB() {
        boolean success = true;
        long oldFreqA = getVfoAFrequency();
        long oldFreqB = getVfoBFrequency();
        writeFrequencyToRadioVfoA(oldFreqB);
        writeFrequencyToRadioVfoB(oldFreqA);           
        return success;
    }
}

