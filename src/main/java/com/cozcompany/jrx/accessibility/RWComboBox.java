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
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComboBox;

/**
 *
 * @author lutusp
 */
public class RWComboBox extends JComboBox<String> implements 
        MouseWheelListener, ActionListener, ControlInterface {

    JRX_TX parent;
    String prefix;
    String token;
    TreeMap<String, Integer> displayMap;
    TreeMap<Integer, String> reverseDisplayMap;
    TreeMap<String, Integer> useMap;
    TreeMap<Double, Integer> useMapDouble;
    TreeMap<Integer, String> reverseUseMap;
    boolean numericMode = false;
    double errorValue = 1e100;
    double oldValue = -1;
    double xValueLow = 0;
    double xValueHigh = 1;
    double yValueLow = 0;
    double yValueHigh = 1;
    double numSelection = -10, oldNumSelection;
    String strSelection = null, oldStrSelection;
    boolean localInhibit = false;
    boolean ctcss = false;
    boolean commOK = true;

    public RWComboBox(JRX_TX p, String pre, String t) {
        super();
        parent = p;
        prefix = pre;
        token = t;
        if (prefix != null) {
            ctcss = (prefix.equals("ctcss"));
        }
        setup(true);
    }

    protected void setup(boolean first) {
        if (first) {
            addMouseWheelListener(this);
            addActionListener(this);
        }
        numericMode = false;
        commOK = true;
        oldNumSelection = -1;
        oldStrSelection = "xxx";        
        displayMap = new TreeMap<>();
        useMap = new TreeMap<>();
        useMapDouble = new TreeMap<>();
        reverseDisplayMap = new TreeMap<>();
        reverseUseMap = new TreeMap<>();
    }

    public void addListItem(String disp, double use, String suse) {
        int index = getItemCount();
        useMapDouble.put(use, index);
        useMap.put(suse, index);
        displayMap.put(disp, index);
        reverseDisplayMap.put(index, disp);
        reverseUseMap.put(index, suse);
        super.addItem(disp);
    }

    protected void setGenericComboBoxScale(
            String tag,
            String search,
            boolean offOption,
            boolean numeric) {
        setup(false);
        boolean old_inhibit = parent.inhibit;
        parent.inhibit = true;
        int index = getSelectedIndex();
        if (parent.radioData != null) {
            try {
                String s = parent.radioData.replaceFirst(search, "$1");
                if (parent.comArgs.debug >= 1) {
                    parent.pout("combo box content: [" + s + "]");
                }
                String[] array = s.split("\\s+");
                String is;
                if (array != null) {
                    removeAllItems();
                    int n = 0;
                    if (offOption) {
                        is = tag + " off";
                        addListItem(is, 0, "0");
                        //stringValues.add(is);
                        //numericValues.add(0.0);
                        //keyToIndex.put(is, n);
                        n += 1;
                    }
                    for (String ss : array) {
                        is = String.format(tag + " %s", ss);
                        if (numeric) {
                            if (ss.matches(".*?[0-9.+-]+.*")) {
                                ss = ss.replaceFirst(".*?([0-9.+-]+).*","$1");
                            } else {
                                break;
                            }
                        }
                        if (ctcss) {
                            double v = Double.parseDouble(ss);
                            ss = String.format("%.0f", v * 10);
                        }
                        //addListItem(is, ss);
                        //parent.pout("combobox argument: " + is);
                        double v = 0;
                        try {
                            v = Double.parseDouble(ss);
                            numericMode = true;
                        } catch (Exception e) {
                        }
                        addListItem(is, v, ss);
                        //keyToIndex.put(ss, n);
                        n += 1;
                        //stringValues.add(ss);
                        //String sv = ss.replaceFirst(".*?(\\d+).*", "$1");
                    }
                } else {
                    comboPlaceholderData();
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        setComboBoxIndex(index);
        parent.inhibit = old_inhibit;
    }

    private void setupFilterCombo() {
        if (parent.radioData != null) {
            removeAllItems();
            // "valToKey" serves some important purposes:
            // it sorts the entries and eliminates duplicates
            TreeMap<Long, String> valToKey = new TreeMap<>();
            //keyToVal.put("BW auto", 0L);
            valToKey.put(0L, "BW auto");
            String s = parent.radioData.replaceFirst(
                                    "(?is).*?Filters:(.*?)Bandwidths.*", "$1");
            Pattern pattern = Pattern.compile("(?is)[0-9.]+ k?hz");
            Matcher m = pattern.matcher(s);
            while (m.find()) {
                String ss = m.group();
                String[] array = ss.split("\\s+");
                ss = "BW " + ss;
                //p("filter: " + array[0] + "," + array[1]);
                double mult = (array[1].equalsIgnoreCase("hz")) ? 1 : 1000;
                double bw = Double.parseDouble(array[0]) * mult;
                long dv = (long) bw;  // bandwidth value, ss is units like khz
                //keyToVal.put(ss, dv);
                valToKey.put(dv, ss);
            }
            for (long dv : valToKey.keySet()) {
                //addListItem(valToKey.get(dv), "" + dv);
                addListItem(valToKey.get(dv), dv, "" + dv);
                if (parent.comArgs.debug >= 1) {
                                    parent.pout("key to filter : [" + dv + "]");
                }
            }
        }
    }

    protected double getFilterBW() {
        int i = getSelectedIndex();
        if (i >= 0 && i < reverseUseMap.size()) {
            return Double.parseDouble(reverseUseMap.get(i));
        }
        return 0;
    }

    protected void setComboBoxIndex(int index) {
        index = Math.max(0, index);
        index = Math.min(index, getItemCount() - 1);
        setSelectedIndex(index);
    }

    protected void comboPlaceholderData() {
        boolean old_inhibit = parent.inhibit;
        parent.inhibit = true;
        int index = getSelectedIndex();
        index = Math.max(0, index);
        removeAllItems();
        for (int i = 1; i < 64; i++) {
            addListItem(String.format("-- n/a %d --", i), i, "" + i);
        }
        setComboBoxIndex(index);
        parent.inhibit = old_inhibit;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        action(evt);
    }

    private void action(ActionEvent evt) {
        writeValue(false);
    }

    @Override
    public void writeValue(boolean force) {
        if (!parent.inhibit && !localInhibit && token != null && isEnabled()) {
            if (numericMode) {
                writeValueNum();
            } else {
                writeValueStr();
            }
        }
    }

    protected void writeValueStr() {
        if (commOK && !parent.inhibit) {
            int index = getSelectedIndex();
            strSelection = reverseUseMap.get(index);
            if (strSelection != null) {
                if (!strSelection.equals(oldStrSelection) ) {
                    String com = String.format("%s %s %s %s", 
                        prefix.toUpperCase(), token, strSelection);
                    parent.sendRadioCom(com, 0, true);
                    oldStrSelection = strSelection;
                }
            }
        }
    }

    protected void writeValueNum() {
        
        if (commOK && !parent.inhibit) {
            numSelection = getConvertedValue();
            if (numSelection != errorValue) {
                String com;
                //parent.pout("numselection: " + numSelection);
                {
                    if (numSelection != oldNumSelection) {
                        if (ctcss) {
                            com = String.format("\\set_ctcss_sql %.0f", 
                                    numSelection);
                        } else {
                            com = String.format("%s %s %.2f", 
                                    prefix.toUpperCase(), token, numSelection);
                        }
                        parent.sendRadioCom(com, 0, true);
                        oldNumSelection = numSelection;
                    }
                }
            }
        }
    }

    protected double readValueNum() {
        if (token != null && isEnabled() && commOK) {
            String com;
            if (ctcss) {
                com = "\\get_ctcss_sql";
            } else {
                com = String.format("%s %s", prefix.toLowerCase(), token);
            }
            numSelection = errorValue;
            String s = parent.sendRadioCom(com, 0, false);
            try {
                numSelection = Double.parseDouble(s);
                oldNumSelection = numSelection;
            } catch (Exception e) {
                //e.printStackTrace(System.out);
            }
        }
        return numSelection;
    }

    protected String readValueStr() {
        String s = "";
        if (token != null && isEnabled() && commOK) {
            String com = String.format("%s %s", prefix.toLowerCase(), token);
            s = parent.sendRadioCom(com, 0, false);
        }
        return s;
    }

    protected double ntrp(double xl, double xh, double yl, double yh, double x) {
        return (x - xl) * (yh - yl) / (xh - xl) + yl;
    }

    @Override
    public void readConvertedValue(double x) {
        if (x != 1e100) {
            numSelection = ntrp(yValueLow, yValueHigh, xValueLow, xValueHigh, x);
            String sn = String.format("%.0f", numSelection);

            localInhibit = true;
            try {
                if (useMapDouble.containsKey(numSelection)) {
                    setSelectedIndex(useMapDouble.get(numSelection));
                } else {
                    // get the closest value
                    Map.Entry<Double, Integer> me = 
                            useMapDouble.lowerEntry(numSelection);
                    if (me == null) {
                        me = useMapDouble.higherEntry(numSelection);
                    }
                    if (me != null) {
                        setSelectedIndex(me.getValue());
                    }
                }
                //parent.pout("read converted value: 
                    //" + sn + ", index " + getSelectedIndex());

            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            localInhibit = false;
        }
    }

    @Override
    public void readConvertedValue() {
        localInhibit = true;
        if (numericMode) {
            readConvertedValue(readValueNum());
        } 
        localInhibit = false;
    }

    protected void inhibitSetItem(String s) {
        try {
            localInhibit = true;
            setSelectedIndex(useMap.get(s));
            localInhibit = false;
        } catch (Exception e) {
        }
    }

    @Override
    public void selectiveReadValue(boolean all) {
        if (isEnabled()) {
            readConvertedValue();
        }
    }

    @Override
    public double getConvertedValue() {
        double v = errorValue;
        try {
            int index = getSelectedIndex();
            String s = reverseUseMap.get(index);
            if (s != null) {
                v = Double.parseDouble(s);
                if (numericMode) {
                    v = ntrp(xValueLow, xValueHigh, yValueLow, yValueHigh, v);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return v;
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
        numericMode = true;
    }

    private void changeIndex(int v) {
        int len = getItemCount();
        if (len > 0) {
            int index = getSelectedIndex() + v;
            index = Math.max(index, 0);
            index = Math.min(index, len - 1);
            setSelectedIndex(index);
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int v = e.getWheelRotation();
        int iv = (v < 0) ? -1 : 1;
        changeIndex(iv);
    }
}
