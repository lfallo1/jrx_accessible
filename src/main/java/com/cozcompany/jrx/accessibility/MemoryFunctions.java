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

import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.TreeMap;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 *
 * @author lutusp
 */
final public class MemoryFunctions {

    JRX_TX parent;
    String header;

    public MemoryFunctions(JRX_TX p) {
        parent = p;
        header = "Mnnn = Freq,Mode,Bandwidth,AGC,CTCSS,StepIndex,NB,Preamp,Antenna,Attenuator,Skip";
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
                confirmEraseButtons(parent.buttonMap);
                break;
        }

    }

    protected void layoutButtons(JPanel panel) {
        parent.buttonMap = new TreeMap<>();
        panel.removeAll();
        panel.setLayout(new GridLayout(20, 20));
        for (int i = 1; i <= parent.memoryButtonTotal; i++) {
            String lbl = String.format("M%03d", i);
            MemoryButton mb = new MemoryButton(lbl, parent);
            panel.add(mb);
            parent.buttonMap.put(lbl, mb);
        }
    }
    
    protected void resetButtonColors() {
        for(MemoryButton mb : parent.buttonMap.values()) {
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
            for (String s : data.split(parent.lineSep)) {
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
        sb.append(header).append(parent.lineSep);
        for (MemoryButton mb : buttonMap.values()) {
            if (mb.frequency >= 0) {
                String rec = String.format("%s = %s" + parent.lineSep, mb.label, mb.toString());
                sb.append(rec);
            }
        }
        return sb.toString();
    }

    protected void readButtonsFromFile(String buttonFilePath, TreeMap<String, MemoryButton> buttonMap) {
        File f = new File(buttonFilePath);
        if (f.exists()) {
            String data = parent.readTextFile(buttonFilePath, parent.lineSep);
            stringToButtons(data, buttonMap, "file");
        }
    }

    protected void writeButtonsToFile(String buttonFilePath, TreeMap<String, MemoryButton> buttonMap) {
        String result = buttonsToString(buttonMap);
        parent.writeTextFile(buttonFilePath, result);

    }

    protected void confirmReadButtonsFromClipboard() {
        if (parent.askUser("Okay to read all memory buttons from clipboard?")) {
            readButtonsFromClipboard();
        }
    }

    protected void readButtonsFromClipboard() {
        String data = parent.readFromClipboard();
        stringToButtons(data, parent.buttonMap, "clipboard");
    }

    protected void writeButtonsToClipboard() {
        String data = buttonsToString(parent.buttonMap);
        parent.writeToClipboard(data);

    }
    
    // This isn't possible yet -- Hamlib doesn't support it

    protected void confirmReadButtonsFromRadio() {
        if (parent.askUser("Okay to read all memory buttons from radio?")) {
            int mem = 0;
            for (MemoryButton mb : parent.buttonMap.values()) {
                parent.waitMS(200);
                parent.p(mb.label);
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
        parent.p(s);
    }
}
