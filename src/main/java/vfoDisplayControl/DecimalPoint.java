/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import javax.swing.JLabel;

/**
 *
 * @author Coz
 */
public class DecimalPoint extends JLabel {
    public static ArrayList<DecimalPoint> points = new ArrayList<DecimalPoint>();
    public DecimalPoint() {
        super();
        setFocusable(false);
        setForeground(Color.GREEN);       
        setText(".");
        Color backTransparent = new Color(0,0,0,0);
        setBackground(backTransparent);      
        setOpaque(false);
        points.add(this);
    }
    public static void setFonts(Font font){
        for (int index=0; index<points.size(); index++){
            points.get(index).setFont(font);
        }        
    }    
}
