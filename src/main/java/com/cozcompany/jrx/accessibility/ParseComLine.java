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

/**
 * Parse the command line arguments.
 * 
 * @author lutusp
 */
final public class ParseComLine {

    public int debug = -1;
    int rigCode = -1;
    String interfaceName = null;
    String rigName = null;
    boolean reset = false;
    boolean runTimer = true;
    JRX_TX parent;

    @Override
    public String toString() {
        return String.format("%d,%d,%s,%s,%s,%s", debug, rigCode,interfaceName, rigName, reset, runTimer);
    }

    public ParseComLine(JRX_TX p, String[] args) {
        parent = p;
        int state = 0;
        try {
            for (String arg : args) {
                switch (state) {
                    case (1):
                        interfaceName = arg;
                        state = 0;
                        break;
                    case (2):
                        rigCode = Integer.parseInt(arg);
                        state = 0;
                        break;
                    case (3):
                        rigName = arg;
                        state = 0;
                        break;
                    case (4):
                        debug = Integer.parseInt(arg);
                        state = 0;
                        break;
                    case (5):
                        runTimer = Integer.parseInt(arg) != 0;
                        state = 0;
                        break;
                    case (0):
                        switch (arg) {
                            case "-r": // interface name as in rigctl
                                state = 1;
                                break;
                            case "-m": // radio code as in rigctl
                                state = 2;
                                break;
                            case "-M": // radio name
                                state = 3;
                                break;
                            case "-d": // debug
                                state = 4;
                                break;
                            case "-t": // timer
                                state = 5;
                                break;
                            case "-i": // initialize to defaults
                                reset = true;
                                break;
                            case "-h": // help
                            default:
                                comHelp();
                                break;
                        }
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }


    }

    private void comHelp() {
        String help =
                  "Usage: -r com port (COM1, /dev/ttyUSB0, etc.)\n"
                + "       -m radio code number (as with rigctl)\n"
                + "       -M \"radio name\" (IC-756, etc., quoted)\n"
                + "       -d (debug) 0,1,2\n"
                + "       -t (run event timer) 0 = no, 1 = yes\n"
                + "       -i initialize radio settings to defaults, no arg\n"
                + "          (doesn't lose memory or other settings)\n"
                + "       -h this help and exit\n"
                + "Requires : Hamlib 4.0 or later.\n"
                + "At the terminal prompt:  rigctl -V  gives the Hamlib version installed.\n";
        System.out.println(help);
        System.exit(0);
    }
    
    public boolean runTimer() {
        return runTimer;
    }
}
