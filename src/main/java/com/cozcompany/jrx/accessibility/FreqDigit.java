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

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JLabel;

/**
 *
 * @author lutusp
 */
final public class FreqDigit extends JLabel implements MouseWheelListener, 
        MouseListener {

    JRX_TX parent;
    float fontScale;
    FreqDigit carry = null;
    private long value = 0;
    private int digitDecade = 0; // This is only used for test routines.
    Color nonZeroColor = new Color(0,192,0);
    Color zeroColor = new Color(0,64,0);

    public FreqDigit(JRX_TX p, int decade, float fs) {
        super();
        parent = p;
        fontScale = fs;
        value = decade;
        digitDecade = decade;
        if (decade >= 0 && decade <= 9) {
            setDigit(decade);
            setForeground(zeroColor);
        } else {
            setText(".");
            setForeground(nonZeroColor);
        }
        setFont(parent.digitsFont);
        addMouseWheelListener(this);
        addMouseListener(this);
    }

    public void setDigit(long v) {
        value = v;
        setText(""+value);
    }

    public void setCarry(FreqDigit fd) {
        carry = fd;
    }
    
    public void setBright(boolean v) {
        setForeground(v?nonZeroColor:zeroColor);
    }

    private void increm(int v) {
        boolean carried = false;
        if (value >= 0) {
            long nv = value + v;
            setDigit((nv + 10) % 10);
            if (carry != null) {
                if (nv > 9) {
                    carry.increm(1);
                    carried = true;
                }
                if (nv < 0) {
                    carry.increm(-1);
                    carried = true;
                }
            }
            // if this is the high digit of the number
            if (!carried) {
                parent.vfoDisplay.digitsToFrequency();
            }
        }
    }

    public long getValue() {
        return value;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int v = e.getWheelRotation();
        int iv = (v < 0) ? 1 : -1;
        increm(iv);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            int my = e.getY();
            int cy = getHeight() / 2;
            int inc = (my < cy) ? 1 : -1;
            increm(inc);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }
}
