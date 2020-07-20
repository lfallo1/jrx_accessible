/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;
import com.cozcompany.jrx.accessibility.ControlInterface;
import com.cozcompany.jrx.accessibility.RigComms.CommsObserver;
import java.awt.Component;
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
        ControlInterface, ActionListener, CommsObserver {
    JRX_TX parent;
    PickAction action;
    public ListDialog dialog;

    String prefix;
    String token;
    String name;
    String title;
    public TreeMap<String, Integer> displayMap;
    TreeMap<Integer, String> reverseDisplayMap;
    TreeMap<String, Integer> useMap;
    TreeMap<Double, Integer> useMapDouble;
    TreeMap<Integer, String> reverseUseMap;
    boolean firstTimeThrough = true;
    boolean commOK = false;  
    public boolean numericMode = false;
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
    public String[] choices = { "choiceOne", "choiceTwo"}; // temporary list for ctor
    

    public RWListButton(JRX_TX aParent, String pre, String aToken, 
            String aName, String aTitle) {
        super();
        parent = aParent;
        prefix = pre;
        token = aToken;
        if (prefix != null) {
            ctcss = (prefix.equals("ctcss"));
        }
        name = aName;
        title = aTitle;
        setup();       
    }
    protected void setup() {
        displayMap = new TreeMap<>();
        useMap = new TreeMap<>();
        useMapDouble = new TreeMap<>();
        reverseDisplayMap = new TreeMap<>();
        reverseUseMap = new TreeMap<>();       
    }
    
    /**
     * Must create components that use "this" pointer after the CTOR is complete.
     */
    public void initialize() {
        if (firstTimeThrough) {
            addActionListener(this);
            parent.rigComms.addObserver(this);
            firstTimeThrough = false;
        } 
        // Fill the maps with demo data.
        if (ctcss) {
            choices = CtcssListButton.TONE_CHOICES;
        } else {
            placeholderData(name);
            choices = getChoiceStrings();
        }

        dialog = new ListDialog(
                parent, 
                (Component)this, 
                name, 
                title,
                selectedIndex,
                choices);               
        action = new PickAction( 
                name,
                null,
                "Select "+name+" from dialog list.",
                null,                
                this,
                dialog);        
        setAction(action);
        setButtonText(choices[selectedIndex]);
        getAccessibleContext().setAccessibleName(
                "Open dialog to choose a "+name+" .");
    }
    
    public void removeAllItems() {
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
    public String getSelectedItem() {
        return reverseDisplayMap.get(selectedIndex);
    }
    public void setSelectedIndex(int index) {
        selectedIndex = index;
        String displayedValue = reverseDisplayMap.get(index);
        setButtonText(displayedValue);
    }
    public void setSelectedItem(String item){
        selectedIndex = displayMap.get(item);
        setButtonText(item);
    }
    /**
     * The RWListButton uses the disp strings in the Dialog list, 
     * the suse strings are read from the rig and written to the rig as command
     * arguments, the "use" values are the actual double representations of the
     * suse strings, and the strings "suse" are representations of whole numbers.
     * All of the maps are versus a common index.
     * 
     * The reason that the disp strings no longer contain the list name is that
     * voiceOver would read that list name over and over as you scroll through the 
     * list which wastes moocho time and is very irritating.  The Dialog title
     * indicates what the list values mean.
     * 
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

    public void placeholderData(String label) {
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



    /**
     * Dynamically change button text to match selected list item.
     * 
     * @TODO allow width of formatted string to be set by var.
     * @param expecting a String with a Name/Label and a selectedValue. 
     */
    public void setButtonText(String value) {
        String str = name+" "+value;
        String formattedText = String.format("%-23S ...", str);
        setText(formattedText);
        getAccessibleContext().setAccessibleDescription(str+" selected");
    }
    /**
     * Return a String array suitable for populating a Dialog list.
     * @return 
     */
    public String[] getChoiceStrings() {
        int size = reverseDisplayMap.size();
        String[] dialogChoices = new String[size];
        for (int index=0; index < size; index++){
            dialogChoices[index] = reverseDisplayMap.get(index);           
        }
        return dialogChoices;
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
                        is = String.format(tag + " %s", ss);  //@TODO Coz remove tag.
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
                    placeholderData(valueLabel);
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

    // Handle clicks on the Set and Cancel buttons, then hide dialog.
    @Override
    public void actionPerformed(ActionEvent evt) {
        if ("Set".equals(evt.getActionCommand())) {            
            dialog.value = (String)(dialog.list.getSelectedValue());
            int index = dialog.list.getSelectedIndex();           
            ((RWListButton)dialog.buttonComp).setSelectedIndex(index);
            if ("Scan Step".equals(dialog.labelTxt) || 
                "Sweep Step".equals(dialog.labelTxt)||
                "Scan Speed".equals(dialog.labelTxt)){
                // This is app setting and radio is not aware of this component.
            } else {
                ((RWListButton)dialog.buttonComp).inhibitSetItem(dialog.value);
                ((RWListButton)dialog.buttonComp).writeValue(true);
            }
            ((RWListButton)dialog.buttonComp).setButtonText(dialog.value);
            dialog.setVisible(false);   
        } else if ("Cancel".equals(evt.getActionCommand())) {
            dialog.setVisible(false);   
        } else if ( "Scan Step".equals(name) ||
                    "Sweep Step".equals(name) || 
                    "Scan Speed".equals(name)) {
            return;
        } else {
            action(evt);
        }                        
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
    
    
    public void writeValueStr() {
        if (commOK && !parent.inhibit) {
            int index = getSelectedIndex();
            strSelection = reverseUseMap.get(index);
            if (strSelection != null) {
                if (!strSelection.equals(oldStrSelection) ) {
                    String com = String.format("%s %s %s ", 
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

    public String readValueStr() {
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
    public void inhibitSetItem(String s) {
        int index = 0;
        try {
            localInhibit = true;
            //String search = ".*"
            index = useMap.get(s);
            setSelectedIndex(index);
        } catch (Exception e) {
            System.out.println("inhibitSetItem had exception : " + e + " and index = "  + index);
        } finally {
            localInhibit = false;
        }
    }
    
    @Override
    public void selectiveReadValue(boolean all) {
        if (isEnabled() && commOK) {
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
    public void update(String event) {
        commOK =  (event == "online");
    }
}
