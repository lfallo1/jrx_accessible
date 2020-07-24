/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.ControlInterface;
import com.cozcompany.jrx.accessibility.JRX_TX;
import com.cozcompany.jrx.accessibility.RigComms.CommsObserver;
import javax.swing.JLabel;

/**
 *
 * @author Coz
 */
public class RWIndicator extends JLabel 
        implements ControlInterface, CommsObserver {
    JRX_TX myParent;
    JRX_TX parent;
    String token;
    String prefix;
    boolean isEnabled = true;
    boolean commOK = false;
    double errorValue = 1e100;
    double oldValue = -1;
    double xValueLow = 0;
    double xValueHigh = 100;            // default range is 0. to 100. displayed
    double yValueLow = 0;               // default range is 0.0 to 1.0 rig value
    double yValueHigh = 1;
    double level = -1, oldLevel = -1;  // range is 0.0 to 1.0 rig value
    boolean localInhibit = false;
    
    public RWIndicator(JRX_TX aParent, String pre, String t) {
        super("");
        myParent = aParent;
        prefix = pre;
        token = t;
        aParent.rigComms.addObserver(this);                       
    }

    protected double ntrp(double xl, double xh, double yl, double yh, double x) {
        return (x - xl) * (yh - yl) / (xh - xl) + yl;
    }

    public double readValue() {
        double localLvl = errorValue;
        if (commOK && token != null && isEnabled()) {
            localLvl = level;
            String com = String.format("%s %s", prefix.toLowerCase(), token);
            String s = myParent.sendRadioCom(com, 0, false);
            try {
                localLvl = Double.parseDouble(s);
            } catch (Exception e) {
            }
        }
        return localLvl;
    }
    
    @Override
    public void setXLow(double x) {
        xValueLow = x;
    }

    @Override
    public void setXHigh(double x) {
        xValueHigh = x;
    }

    @Override
    public void setYLow(double y) {
        yValueLow = y;
    }

    @Override
    public void setYHigh(double y) {
        yValueHigh = y;
    }

    @Override
    public double getConvertedValue() {
        return ntrp(xValueLow, xValueHigh, yValueLow, yValueHigh, readValue());
    }

    @Override
    public void readConvertedValue(double x) {
        int y = (int) (ntrp(yValueLow, yValueHigh, xValueLow, xValueHigh, x) + 0.5);
        localInhibit = true;
        level = y;
        localInhibit = false;
    }

    @Override
    public void readConvertedValue() {
        level = readValue();
        if (level != errorValue && level != oldLevel) {
            readConvertedValue(level);
            oldLevel = level;
        }
    }

    @Override
    public void selectiveReadValue(boolean all) {
        if (isEnabled()) { 
            readConvertedValue();
        }
    }

    @Override
    public void writeValue(boolean force) {
        // This control does not write.
    }

    @Override
    public void update(String event) {
        commOK = ("online".equals(event));
    }
    
}
