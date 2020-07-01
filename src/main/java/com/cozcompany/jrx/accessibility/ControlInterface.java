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

import java.awt.Component;

/**
 *
 * @author lutusp
 */
public interface ControlInterface {
    public void setXLow(double x);
    public void setXHigh(double x);
    public void setYLow(double y);
    public void setYHigh(double y);
    public double getConvertedValue();
    public void readConvertedValue(double x);
    public void readConvertedValue();
    public void selectiveReadValue(boolean all);
    /** Write value to radio when enabled.  
     * @param force To force a write even if value has
     * not changed set force true.
     */
    public void writeValue(boolean force);
    /**
     * Examine the reply capabilities to see if this control is to be enabled.
     * @param source is the capabilities string from the radio.
     * @param search is the regex search expression
     * @param hasLevel is true when the control has a numeric level (0..1)
     * @return 
     */
    default boolean enableCap(String source, String search, boolean hasLevel) {
        
        boolean enabled = (source != null && source.matches(search + ".*"));
        ((Component)this).setEnabled(enabled);
        if (enabled && hasLevel) {
            try {
                // This class is an instance of ControlInterface.
                String range = source.replaceFirst(search + 
                        "([0-9+-]+)\\.\\.([0-9+-]+).*", "$1,$2");
                String[] digits = range.split(",");
                int low = Integer.parseInt(digits[0]);
                int high = Integer.parseInt(digits[1]);
                if (high - low == 0) {
                    low = 0;
                    high = 1;
                }
                ControlInterface box = (ControlInterface) this;
                //(source + " control cap:" + low + "," + high);
                box.setYLow(low);
                box.setYHigh(high);                
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        return enabled;    
    }    

    
    
    
    
    
//    default boolean enableCap(ControlInterface cc, 
//            String source, String search, boolean hasLevel ) {
//        
//        boolean enabled = (source != null && source.matches(search + ".*"));
//        ((Component)cc).setEnabled(enabled);
//        if (enabled && hasLevel) {
//            try {
//                // This class is an instance of ControlInterface.
//                String range = source.replaceFirst(search + 
//                        "([0-9+-]+)\\.\\.([0-9+-]+).*", "$1,$2");
//                String[] digits = range.split(",");
//                int low = Integer.parseInt(digits[0]);
//                int high = Integer.parseInt(digits[1]);
//                if (high - low == 0) {
//                    low = 0;
//                    high = 1;
//                }
//                ControlInterface box = (ControlInterface) cc;
//                //(source + " control cap:" + low + "," + high);
//                box.setYLow(low);
//                box.setYHigh(high);                
//            } catch (Exception e) {
//                e.printStackTrace(System.out);
//            }
//        }
//        return enabled;    
//    }    
}
