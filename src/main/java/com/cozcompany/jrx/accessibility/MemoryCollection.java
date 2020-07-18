// ***************************************************************************
// *   Copyright (C) 2012 by Paul Lutus                                      *
// *   lutusp@arachnoid.com                                                  *
// *                                                                         *
// *   This program is free software; you can redistribute it and/or modify  *
// *   it under the terms of the GNU General Public License as published by  *
// *   the Free Software Foundation; either version 2 of the License, or     *
// *   (audibleTip your option) any later version.                                   *
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

import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeMap;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Each memory is represented by a MemoryButton which is collected in a TreeMap
 * where each memory button is mapped to a string key, "buttonMap".
 * 
 * @author lutusp
 */
final public class MemoryCollection {

    JRX_TX parent;
    String header;
    TreeMap<String, MemoryButton> buttonMap;
    String sv_mostRecentButton = "";
    String filePath;
    

    public MemoryCollection(JRX_TX p) {
        parent = p;
        header = "Mnnn = Freq,Mode,Bandwidth,AGC,CTCSS,StepIndex,NB,Preamp,Antenna,Attenuator,Skip";
    }
    protected void readMemoryButtons() {
        layoutButtons(parent.memoryButtonsPanel);
        readButtonsFromFile(filePath, buttonMap);
    }

    protected void writeMemoryButtons() {
        writeButtonsToFile(filePath, buttonMap);
    }

    public void setFilePath(String path) {
        filePath = path;        
    }
    
    protected void dispatch(MouseEvent evt) {
        String jb = ((JButton) evt.getSource()).getText();
        switch (jb) {
            case "CM":
                writeButtonsToClipboard();
                break;
            case "PM":
                confirmReadButtonsFromClipboard();
                break;
            case "RM":
                confirmReadButtonsFromRadio();
                break;
            case "WM":
                confirmWriteButtonsToRadio();
                break;
            case "EM":
                confirmEraseButtons(buttonMap);
                break;
        }

    }

    protected void layoutButtons(JPanel panel) {
        buttonMap = new TreeMap<>();
        panel.removeAll();
        panel.setLayout(new GridLayout(20, 20));
        for (int i = 1; i <= parent.memoryButtonTotal; i++) {
            String lbl = String.format("M%03d", i);
            MemoryButton mb = new MemoryButton(lbl, parent);
            panel.add(mb);
            buttonMap.put(lbl, mb);
        }
    }
    
    protected void resetButtonColors() {
        for(MemoryButton mb : buttonMap.values()) {
            mb.updateState();
        }
    }

    protected void confirmEraseButtons(TreeMap<String, MemoryButton> buttonMap) {
        if (parent.askUser("Okay to erase all memory buttons?")) {
            eraseButtons(buttonMap);
        }
    }

    protected void eraseButtons(TreeMap<String, MemoryButton> buttonMap) {
        for (MemoryButton mb : buttonMap.values()) {
            mb.reset();
            mb.updateState();
        }
    }

    protected void stringToButtons(String data, TreeMap<String, MemoryButton> buttonMap, String tag) {
        try {
            boolean first = true;
            for (String s : data.split(parent.LINE_SEP)) {
                if (first) {
                    if (!s.equals(header)) {
                        throw new Exception(String.format("%s contents not a button table for this JRX version.", tag));
                    }
                    first = false;
                } else {
                    String[] array = s.split(" = ");
                    if (buttonMap.containsKey(array[0])) {
                        buttonMap.get(array[0]).defineFromString(array[1]);
                    }
                }
            }
        } catch (Exception e) {
            parent.tellUser(String.format("Error: %s", e.getMessage()));
        }
    }

    protected String buttonsToString(TreeMap<String, MemoryButton> buttonMap) {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(parent.LINE_SEP);
        for (MemoryButton mb : buttonMap.values()) {
            if (mb.frequency >= 0) {
                String rec = String.format("%s = %s" + parent.LINE_SEP, mb.label, mb.toString());
                sb.append(rec);
            }
        }
        return sb.toString();
    }

    protected void readButtonsFromFile(String filePath, TreeMap<String, MemoryButton> buttonMap) {
        File f = new File(filePath);
        if (f.exists()) {
            String data = parent.readTextFile(filePath, parent.LINE_SEP);
            stringToButtons(data, buttonMap, "file");
        }
    }

    protected void writeButtonsToFile(String filePath, TreeMap<String, MemoryButton> buttonMap) {
        String result = buttonsToString(buttonMap);
        parent.writeTextFile(filePath, result);

    }

    protected void confirmReadButtonsFromClipboard() {
        if (parent.askUser("Okay to read all memory buttons from clipboard?")) {
            readButtonsFromClipboard();
        }
    }

    protected void readButtonsFromClipboard() {
        String data = parent.readFromClipboard();
        stringToButtons(data, buttonMap, "clipboard");
    }

    protected void writeButtonsToClipboard() {
        String data = buttonsToString(buttonMap);
        parent.writeToClipboard(data);

    }
    
    // This isn't possible yet -- Hamlib doesn't support it

    protected void confirmReadButtonsFromRadio() {
        if (parent.askUser("Okay to read all memory buttons from radio?")) {
            int mem = 0;
            for (MemoryButton mb : buttonMap.values()) {
                parent.waitMS(200);
                parent.pout(mb.label);
                String com = String.format("E %d",mem);
                parent.sendRadioCom(com,0,true);
                parent.waitMS(200);
                com = String.format("e %d",mem);
                String result = parent.sendRadioCom(com,0,false);
                //p("mem result: " + result);
                mem += 1;
            }
        }
    }

    protected void confirmWriteButtonsToRadio() {
        if (parent.askUser("Okay to write all memory buttons to radio?")) {
        }
    }
    
    // a shorthand function for debugging
    public void p(String s) {
        parent.pout(s);
    }
    
    
    protected ArrayList<MemoryButton> getScanButtons(int max) {
        ArrayList<MemoryButton> array = null;
        boolean validFreqs = false;
        try {
            if (sv_mostRecentButton == null || !sv_mostRecentButton.matches("(?i)^m.*")) {
                sv_mostRecentButton = "M001";
            }
            array = new ArrayList<>();
            int n = 0;
            Iterator<MemoryButton> ss = buttonMap.tailMap(sv_mostRecentButton).values().iterator();
            while (ss.hasNext() && n < max) {
                MemoryButton button = ss.next();
                if (button.frequency >= 0) {
                    array.add(button);
                    boolean skip = (button.skipDuringScan != 0);
                    button.updateState(parent.darkGreen);
                    if (!skip) {
                        validFreqs = true;
                    }
                } else {
                    break;
                }
                n++;
            }
            if (n < 2) {
                validFreqs = false;
                array = new ArrayList<>();
                n = 0;
                // search in reverse
                ArrayList<MemoryButton> revList = 
                        new ArrayList<>(buttonMap.headMap(sv_mostRecentButton, true).values());
                Collections.reverse(revList);
                //revmap.;
                ss = revList.iterator();
                while (ss.hasNext() && n < max) {
                    MemoryButton button = ss.next();
                    if (button.frequency >= 0) {
                        array.add(button);
                        boolean skip = (button.skipDuringScan != 0);
                        button.updateState(parent.darkGreen);
                        if (!skip) {
                            validFreqs = true;
                        }
                    } else {
                        break;
                    }
                    n++;
                }
                // now reverse the result
                Collections.reverse(array);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        if (!validFreqs) {
            parent.scanStateMachine.noValidFrequenciesPrompt();
            return null;
        } else {
            return array;
        }
    }    
}
