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

import components.RWListButton;
import java.awt.Rectangle;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.TreeMap;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

/**
 * Configuration Manager class to retreive and save Swing control settings when
 * the control name begins with "sv_" which probably stands for "save".
 * @author lutusp
 */
final public class ConfigManager {

    String appName;
    String lineSep;
    String fileSep;
    String userDir;
    String userPath;
    String initPath;
    String fieldPrefix = "sv_";
    JFrame parent;
    TreeMap<String, Field> fields;
    
    public ConfigManager() {
    }

    public ConfigManager(JFrame parent) {
        this.parent = parent;
        lineSep = System.getProperty("line.separator");
        fileSep = System.getProperty("file.separator");
        appName = parent.getClass().getSimpleName();
        userDir = System.getProperty("user.home");
        userPath = userDir + fileSep + "." + appName;
        initPath = userPath + fileSep + appName + ".ini";
        setup();
    }

    private void setup() {
        fields = new TreeMap<>();
        String name;
        Field[] fieldArray = parent.getClass().getDeclaredFields();
        for (Field f : fieldArray) {
            name = f.getName();
            if (name.matches("^" + fieldPrefix + ".*")) {
                fields.put(name, f);
            }
        }
    }

    public void read() {
        String data = readTextFile(initPath, lineSep);
        if (data.length() > 0) {
            for (String item : data.split(lineSep)) {
                // the change to "limit 2" allows values to contain equals signs
                String[] lFields = item.split(" = ",2);
                if (lFields.length > 1) {
                    //System.out.println("read() " + lFields[0] + " and " + lFields[1]);
                    readWriteField(lFields[0].trim(), lFields[1].trim());
                }
            }
        }
    }

    public void write() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Field> e : fields.entrySet()) {
            String s = String.format("%s = %s%s", e.getKey(), 
                            readWriteField(e.getKey(), null), lineSep);
            sb.append(s);
            //System.out.println("write() s "+s);
        }
        writeTextFile(initPath, sb.toString());
        
    }

    private int minmax(int n, int low, int high) {
        n = Math.min(n, high - 1);
        n = Math.max(n, low);
        return n;
    }
    /**
     * Read or Write the selected value from or to a control.  THIS IS NOT
     * THE CONVENTIONAL SENSE OF READ OR WRITE TO PERSISTENT STORAGE.  When
     * "write" is true, the value is actually read from persistence and written
     * to the class instance.  When "write" is false, the value is read from
     * the class instance and written to persistent storage.
     * @param name
     * @param value
     * @return 
     */
    String readWriteField(String name, String value) {
        try {
            Class pc = parent.getClass();
            //System.out.println("looking for [" + pair[0] + "]");
            Field f = pc.getDeclaredField(name);
            Object obj = f.get(parent); // get the class instance
            String classType = f.getType().toString();
            classType = classType.replaceFirst(".*\\.(.*)", "$1");
//            System.out.println("readWriteField() name: " + name + ", classtype: " 
//                    + classType );
            boolean write = true;
            if (Objects.isNull(value)) {
                write = false;
            } else if  (value.contains("null")) {
                write = false;                
            }
            if (write) {
                    value = value.trim();
            }
            switch (classType) {
                case ("int"):
                    if (write) {
                        f.setInt(parent, Integer.parseInt(value));
                    } else {
                        value = "" + f.getInt(parent);
                    }
                    break;
                case ("long"):
                    if (write) {
                        f.setLong(parent, Long.parseLong(value));
                    } else {
                        value = "" + f.getLong(parent);
                    }
                    break;
                case ("double"):
                    if (write) {
                        f.setDouble(parent, Double.parseDouble(value));
                    } else {
                        value = "" + f.getDouble(parent);
                    }
                    break;
                case ("MutableDouble"):
                    if (write) {
                        ((MutableDouble) obj).v = Double.parseDouble(value);
                    } else {
                        value = "" + ((MutableDouble) obj).v;
                    }
                    break;
                case ("boolean"):
                    if (write) {
                        f.setBoolean(parent, value.equals("true"));
                    } else {
                        value = "" + f.getBoolean(parent);
                    }
                    break;
                case ("String"):
                    if (write) {
                        f.set(parent, value);
                    } else {
                        value = (String) f.get(parent);
                    }
                    break;
                case ("Rectangle"):
                    if (write) {
                        Rectangle r = parseRectangle(value);
                        if (r != null) {
                            f.set(parent, r);
                        }
                    } else {
                        value = (String) f.get(parent).toString();
                    }
                    break;
                case ("JRadioButton"):
                    JRadioButton button = (JRadioButton) obj;
                    if (write) {
                        button.setSelected(value.equals("true"));
                    } else {
                        value = "" + button.isSelected();
                    }
                    break;
                case ("JCheckBox"):
                    JCheckBox cb = (JCheckBox) obj;
                    if (write) {
                        cb.setSelected(value.equals("true"));
                    } else {
                        value = "" + cb.isSelected();
                    }
                    break;
                case ("JTextField"):
                    JTextField tf = (JTextField) obj;
                    if (write) {
                        tf.setText(value);
                    } else {
                        value = tf.getText();
                    }
                    break;
                case ("JComboBox"):
                    JComboBox box = (JComboBox) obj;
                    if (write) {
                        int n = Integer.parseInt(value);
                        n = minmax(n, 0, box.getItemCount());
                        box.setSelectedIndex(n);
                    } else {
                        value = "" + box.getSelectedIndex();
                    }
                    break;
                case ("JButton"): // Handle RWListButton
                    RWListButton listButton = (RWListButton) obj;
                    if (write) {
                        int n = Integer.parseInt(value);
                        n = minmax(n, 0, listButton.getItemCount());
                        listButton.setSelectedIndex(n);
                    } else {
                        value = "" + listButton.getSelectedIndex();
                    }
                    break;
                case ("JSpinner"):
                    JSpinner jsp = (JSpinner) obj;
                    if (write) {
                        jsp.setValue(Integer.parseInt(value));
                    } else {
                        value = "" + jsp.getValue();
                    }
                    break;
                case ("JSlider"):
                    JSlider jsl = (JSlider) obj;
                    if (write) {
                        jsl.setValue(Integer.parseInt(value));
                    } else {
                        value = "" + jsl.getValue();
                    }
                    break;
                case ("JTabbedPane"):
                    JTabbedPane tp = (JTabbedPane) obj;
                    if (write) {
                        int n = Integer.parseInt(value);
                        n = minmax(n, 0, tp.getTabCount());
                        tp.setSelectedIndex(n);
                    } else {
                        value = "" + tp.getSelectedIndex();
                    }
                    break;
                case ("JFrame"):
                    // only to set screen geometry
                    JFrame jf = (JFrame) obj;
                    if (write) {
                        Rectangle r = parseRectangle(value);
                        if (r != null) {
                            jf.setBounds(r);
                        }
                    } else {
                        value = "" + jf.getBounds();
                    }
                    break;
                default:
                    if (obj instanceof Configurable) {
                        if (write) {
                            ((Configurable) obj).fromString(value);
                        } else {
                            value = obj.toString();
                        }
                    } else {
                        System.out.println(getClass().getName() + ": cannot decode value for " + classType + " (" + name + ")");
                    }
            }
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            System.out.println(getClass().getName() + ":readWriteField: \"" + name + "\": " + e);
        }
        return value;
    }

    Rectangle parseRectangle(String value) {
        ArrayList<Integer> vi = new ArrayList<>();
        Rectangle rect = null;
        try {
            String s = value.replaceFirst("^.*x=([-|\\d]+).*y=([-|\\d]+).*width=([-|\\d]+).*height=([-|\\d]+).*$", "$1,$2,$3,$4");
            for (String is : s.split(",")) {
                vi.add(Integer.parseInt(is));
            }
            Iterator<Integer> it = vi.iterator();
            rect = new Rectangle(it.next(), it.next(), it.next(), it.next());
        } catch (Exception e) {
            System.out.println(getClass().getName() + ": error: " + e);
        }
        return rect;
    }

    public String readTextFile(String path, String lineSep) {
        String result = "";
        try {
            result = new Scanner(new File(path)).useDelimiter("\\Z").next();
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return result;
    }

    public void writeTextFile(String path, String data) {
        try (PrintWriter out = new PrintWriter(new File(path))) {
            out.write(data);
            out.close();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    // a shorthand function for debugging
    private void p(String s) {
        System.out.println(s);
    }
}
