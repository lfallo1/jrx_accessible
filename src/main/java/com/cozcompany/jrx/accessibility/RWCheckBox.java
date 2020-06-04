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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;

/**
 *
 * @author lutusp
 */
public class RWCheckBox extends JCheckBox implements ActionListener, ControlInterface {

    JRX_TX parent;
    String token;
    String prefix;
    boolean state, oldState;
    boolean localInhibit = false;

    public RWCheckBox(JRX_TX p, String pre, String t) {
        super();
        parent = p;
        prefix = pre;
        token = t;
        setup(true);
    }

    private void setup(boolean first) {
        if (first) {
            addActionListener(this);
        }
    }

    @Override
    public void writeValue(boolean force) {
        if (!parent.inhibit && !localInhibit && token != null) {
            state = isSelected();
            if (force || state != oldState) {
                String com = String.format("%s %s %s", prefix.toUpperCase(), token, state ? "1" : "0");
                parent.sendRadioCom(com, 0, true);
                oldState = state;
            }
        }
    }

    protected void readValue() {
        if (token != null) {
            String com = String.format("%s %s", prefix.toLowerCase(), token);
            String s = parent.sendRadioCom(com, 0, false);
            if (s != null) {
                state = s.equals("1");
                oldState = state;
                localInhibit = true;
                setSelected(state);
                localInhibit = false;
            }
        }
    }

    @Override
    public double getConvertedValue() {
        return (isSelected()) ? 1.0 : 0.0;
    }

    @Override
    public void readConvertedValue(double v) {
        setSelected(v != 0.0);
    }

    @Override
    public void readConvertedValue() {
        localInhibit = true;
        readValue();
        localInhibit = false;
    }

    @Override
    public void selectiveReadValue(boolean all) {
        if (isEnabled()) {
            readConvertedValue();
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        writeValue(false);
    }

    @Override
    public void setXLow(double x) {
    }

    @Override
    public void setXHigh(double x) {
    }

    @Override
    public void setYLow(double y) {
    }

    @Override
    public void setYHigh(double y) {
    }
}
