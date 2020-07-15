/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;
import com.cozcompany.jrx.accessibility.ControlInterface;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JButton;

/**
 * A replacement for the JComboBox that actually supports voiceOver.
 * 
 * @author Coz
 */
public class RWListButton extends JButton  implements 
        ControlInterface, ActionListener {
    JRX_TX parent;
    PickAction action;
    String prefix;
    String token;
    TreeMap<String, Integer> displayMap;
    TreeMap<Integer, String> reverseDisplayMap;
    TreeMap<String, Integer> useMap;
    TreeMap<Double, Integer> useMapDouble;
    TreeMap<Integer, String> reverseUseMap;
    boolean inConstructor = true;
    boolean commOK = true;  // @TODO Coz fix this so it really indicates comms status....
    boolean numericMode = false;
    boolean ctcss = false;
    boolean localInhibit = false;
    int selectedIndex = 0;
    int oldIndex = -1;
    String oldStrSelection = "xxx";
    String strSelection = null;
    double numSelection = -10, oldNumSelection;
    double errorValue = 1e100;
    double oldValue = -1;
    double xValueLow = 0;
    double xValueHigh = 1;
    double yValueLow = 0;
    double yValueHigh = 1;
    String valueLabel = "";

    

    public RWListButton(JRX_TX aParent, String pre, String aToken) {
        super();
        parent = aParent;
        prefix = pre;
        token = aToken;
        if (prefix != null) {
            ctcss = (prefix.equals("ctcss"));
        }       
        setup();       
    }
    final protected void setup() {
        if (inConstructor) {
            addActionListener(this);
            inConstructor = false;
        }             
        displayMap = new TreeMap<>();
        useMap = new TreeMap<>();
        useMapDouble = new TreeMap<>();
        reverseDisplayMap = new TreeMap<>();
        reverseUseMap = new TreeMap<>();       
    }
    protected void removeAllItems() {
        displayMap.clear();
        useMap.clear();
        useMapDouble.clear();
        reverseDisplayMap.clear();
        reverseUseMap.clear();
    }
    /**
     * Return the number of items that have been added to the display list.
     * @return int.
     */
    public int getItemCount() {
        return displayMap.size();
    }
    public int getSelectedIndex() {
        return selectedIndex;
    }   
    public void setSelectedIndex(int index) {
        selectedIndex = index;
        String displayedValue = reverseDisplayMap.get(index);
        setButtonText(displayedValue);
    }
    /**
     * The RWListButton uses the disp strings in the Dialog list and formats
     * that disp string for the button text.  It reads the suse string from the
     * rig and writes the suse string to the rig. The values represented by "use"
     * "suse" are 10 times the displayed float making them whole numbers.
     * All of the maps are vs a common index.
     * @param disp a string like "107.2"
     * @param use   a double like 1072.000
     * @param suse a string like "1072.0"
     */
    public void addListItem(String disp, double use, String suse) {
        int index = getItemCount();
        useMapDouble.put(use, index);
        useMap.put(suse, index);
        displayMap.put(disp, index);
        reverseDisplayMap.put(index, disp);
        reverseUseMap.put(index, suse);
    }
    /**
     * Dynamically change button text to match selected list item.
     * 
     * @todo allow width of formatted string to be set by var.
     * @param str 
     */
    public void setButtonText(String value) {
        String str = prefix + " " + value;
        String formattedText = String.format("%-23S ...", str);
        setText(formattedText);
        getAccessibleContext().setAccessibleDescription(str+" selected");
    }

    public String[] getChoiceStrings() {
        int size = reverseDisplayMap.size();
        String[] choices = new String[size];
        for (int index=0; index < size; index++){
            choices[index] = reverseDisplayMap.get(index);           
        }
        return choices;
    }

    public void setContent(
            String label,
            int start,
            int end,
            int step,
            int xlow,
            int xhigh,
            int ylow,
            int yhigh,
            int initial) {
        removeAllItems();
        valueLabel = label;
        for (int i = start; i <= end; i += step) {
            String s = String.format("%s %d", label, i);
            addListItem(s, i, "" + i);
        }
        setXLow(xlow);
        setXHigh(xhigh);
        // To avoid resetting values, make yhigh = ylow.
        if (yhigh > ylow) {
            setYLow(ylow);
            setYHigh(yhigh);
        }
        setSelectedIndex(useMap.get("" + initial));
    }

    public void setGenericScale(
            String tag,
            String search,
            boolean offOption,
            boolean numeric) {
        setup();
        boolean old_inhibit = parent.inhibit;
        parent.inhibit = true;
        int index = getSelectedIndex();
        if (parent.radioData != null) {
            try {
                String s = parent.radioData.replaceFirst(search, "$1");
                if (parent.comArgs.debug >= 1) {
                    parent.pout("listButton content: [" + s + "]");
                }
                String[] array = s.split("\\s+");
                String is;
                if (array != null) {
                    removeAllItems();
                    int n = 0;
                    if (offOption) {
                        is = tag + " off";
                        addListItem(is, 0, "0");
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
                        double v = 0;
                        try {
                            v = Double.parseDouble(ss);
                            numericMode = true;
                        } catch (Exception e) {
                        }
                        addListItem(is, v, ss);
                        n += 1;
                    }
                } else {
                    listButtonPlaceholderData(valueLabel);
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        setListButtonIndex(index);
        parent.inhibit = old_inhibit;
    }

    protected void setListButtonIndex(int index) {
        index = Math.max(0, index);
        index = Math.min(index, getItemCount() - 1);
        setSelectedIndex(index);
    }

    protected void listButtonPlaceholderData(String label) {
        boolean old_inhibit = parent.inhibit;
        parent.inhibit = true;
        int index = getSelectedIndex();
        index = Math.max(0, index);
        removeAllItems();
        for (int i = 1; i < 64; i++) {
            
            addListItem(String.format("%s -- n/a %d --", label, i),  i, "" + i);
        }
        setListButtonIndex(index);
        parent.inhibit = old_inhibit;
    }


    @Override
    public void actionPerformed(ActionEvent evt) {
        action(evt);
    }

    private void action(ActionEvent evt) {
        writeValue(true);
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
                            com = String.format("\\set_ctcss_tone %.0f", 
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
                com = "\\get_ctcss_tone";
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
    /**
     * Set the selected item based on the given display string while the localInhibit
     * is enabled.
     * @param s the display string.
     */
    protected void inhibitSetItem(String s) {
        try {
            localInhibit = true;
            setSelectedIndex(displayMap.get(s));
        } catch (Exception e) {           
        } finally {
            localInhibit = false;
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

//    @Override
//    public void mouseWheelMoved(MouseWheelEvent e) {
//        int v = e.getWheelRotation();
//        int iv = (v < 0) ? -1 : 1;
//        changeIndex(iv);
//    }

    


}
