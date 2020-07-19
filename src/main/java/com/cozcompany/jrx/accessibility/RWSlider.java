// ***************************************************************************
// *   Copyright (C) 2012 by Paul Lutus                                      *
// *   lutusp@arachnoid.com                                                  *
// *                                                                         *
// *   This program is free software; you can redistribute it and/or modify  *
// *   it under the terms of the GNU General Public License as published by  *
// *   the Free Software Foundation; either version 2 of the License, or     *
// *   (at your option) any later version.                                   *
// *                                                                         *
// *   This program is distributed in the hope that it will be useful,       *
// *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
// *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
// *   GNU General Public License for more details.                          *
// *                                                                         *
// *   You should have received a copy of the GNU General Public License     *
// *   along with this program; if not, write to the                         *
// *   Free Software Foundation, Inc.,                                       *
// *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
// ***************************************************************************
package com.cozcompany.jrx.accessibility;

import com.cozcompany.jrx.accessibility.RigComms.CommsObserver;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author lutusp
 */
public class RWSlider extends JSlider implements MouseWheelListener, 
        ChangeListener, ControlInterface, CommsObserver {

    JRX_TX parent;
    String token;
    String prefix;
    int initial = 0;
    double errorValue = 1e100;
    double oldValue = -1;
    double xValueLow = 0;
    double xValueHigh = 100;
    double yValueLow = 0;
    double yValueHigh = 1;
    double level = -1, oldLevel = -1;
    boolean localInhibit = false;
    boolean commOK = false;

    public RWSlider(JRX_TX p, String pre, String t, int initial) {
        super();
        parent = p;
        this.prefix = pre;
        this.token = t;
        this.initial = initial;
        setMajorTickSpacing(10);
        setMinorTickSpacing(5);
        setPaintTicks(true);
        setup(true);

    }

    private void setup(boolean first) {
        if (first) {
            addMouseWheelListener(this);
            addChangeListener(this);
        }
        setValue(initial);
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
        writeValue(false);
    }

    public void writeValue(double v, boolean force) {
        readConvertedValue(v);
        writeValue(force);
    }

    @Override
    public void writeValue(boolean force) {
        if (commOK  || force) {
//            try {
//                parent.resetTimer();
//            }
//            catch (Exception e) {
//                System.out.println("Had an exceptional exception");
//            }
            if (!parent.inhibit && !localInhibit && isEnabled()) {
                level = getConvertedValue();
                // @TODO Coz this does not make sense.... and was reported as a bug.  FIX THIS.
                if(parent.squelchScheme.useJRXSquelch && token.equals("AF")) {
                    level = (parent.squelchScheme.isSquelchOpen())?level:0;
                }
                if (force || level != oldLevel) {
                    String com = String.format("%s %s %.2f", prefix.toUpperCase(), token, level);
                    parent.sendRadioCom(com, 0, true);
                    oldLevel = level;
                }
            }
        }
    }

    protected double readValue() {
        double localLvl = errorValue;
        if (commOK && token != null && isEnabled()) {
            localLvl = level;
            String com = String.format("%s %s", prefix.toLowerCase(), token);
            String s = parent.sendRadioCom(com, 0, false);
            try {
                localLvl = Double.parseDouble(s);
            } catch (Exception e) {
            }
        }
        return localLvl;
    }
    
    protected double ntrp(double xl, double xh, double yl, double yh, double x) {
        return (x - xl) * (yh - yl) / (xh - xl) + yl;
    }

    @Override
    public void readConvertedValue(double x) {
        int y = (int) (ntrp(yValueLow, yValueHigh, xValueLow, xValueHigh, x) + 0.5);
        localInhibit = true;
        setValue(y);
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
        if (isEnabled()) { //(all || (!token.equals("SQL") && !token.equals("AF")))) {
            readConvertedValue();
        }
    }

    @Override
    public double getConvertedValue() {
        return ntrp(xValueLow, xValueHigh, yValueLow, yValueHigh, getValue());
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
    public void mouseWheelMoved(MouseWheelEvent e) {
        int v = e.getWheelRotation();
        int iv = (v < 0) ? -1 : 1;
        setValue(getValue() + iv);
    }

    @Override
    public void setValue(int v) {
        super.setValue(v);
    }
    
    @Override
    public void update(String event) {
        commOK =  (event == "online");
    }

}
