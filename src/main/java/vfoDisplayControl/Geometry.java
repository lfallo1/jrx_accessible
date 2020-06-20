/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;

/**
 * Abstract Geometry class for drawing a geometric shape and storing it in a
 * polymorphic container.  Allows constructing a composite of all the stored
 * shapes by drawing consecutive stored shapes.
 * 
 * Variable bounds is the bounding rectangle for the geometric shape.
 * 
 * @author Coz
 */


public abstract class Geometry {
    Rectangle bounds;
    Color usedColor;
    boolean doFill;
    int sides;
    String name;
    protected Dimension dims;

    public Geometry(Rectangle rbounds, Color color, boolean isFilled, int qtySides, String geoName) {
        bounds = rbounds;           
        usedColor = color;
        doFill = isFilled;
        sides = qtySides;
        name = geoName;
    }
    public abstract void draw(Graphics g);    

    public void setDims(Dimension dim) {dims = dim;}
    
    public Rectangle getScaledBounds() {
        if (dims == null) {
            dims = new Dimension(bounds.width, bounds.height);
        }
        Rectangle scaledBounds = 
                new Rectangle(  (int)(bounds.x),
                                (int)(bounds.y),
                                (int)(dims.width),
                                (int)(dims.height));
        return scaledBounds;
    }

 
   
    /**
     * For the given JLabel, use the label dimensions and font metrics to 
     * determine the maximum font size that will fit.
     * 
     * @param label the JLabel text field 
     * 
     * @return The maximum font size in font points
     */
    public static int computeMaxFontSize(JLabel label) {
        // Given the label dimensions, the text, and the font, 
        // what is the font size that will fit?
        
        // Compute the font size to fit into the label size.
        Font labelFont = label.getFont();
        String labelText = label.getText();

        int stringWidth = label.getFontMetrics(labelFont).stringWidth(labelText);
        int componentWidth = label.getWidth();

        // Find out how much the font can grow in width.
        double widthRatio = (double)componentWidth / (double)stringWidth;
        System.out.println("widthRatio of "+labelFont.getName()+" is " + String.valueOf(widthRatio) );
        int newFontSize = (int)(labelFont.getSize() * widthRatio);
        int componentHeight = label.getHeight();

        // Pick a new font size so it will not be larger than the height of label.
        int fontSizeToUse = Math.min(newFontSize, componentHeight);
        System.out.println("Font size to use is "+ String.valueOf(fontSizeToUse));
        return fontSizeToUse;
    }
 
    
    /**
     * For the given formatted text field, use the field dimensions and font 
     * metrics to determine the maximum font size that will fit.
     * Note: from experience, the font size returned is about 20 percent too big.
     * 
     * @param ftf the formatted text field 
     * 
     * @return The maximum font size in font points
     */
    public static int computeMaxFontSize(JFormattedTextField ftf) {
        // Given the label dimensions, the text, and the font, 
        // what is the font size that will fit?
        
        // Compute the font size to fit into the label size.
        Font font = ftf.getFont();
        String text = ftf.getText();

        int stringWidth = ftf.getFontMetrics(font).stringWidth(text);
        int componentWidth = ftf.getWidth();

        // Find out how much the font can grow in width.
        double widthRatio = (double)componentWidth / (double)stringWidth;
        System.out.println("widthRatio of "+font.getName()+" is " + String.valueOf(widthRatio) );
        int newFontSize = (int)(font.getSize() * widthRatio);
        int componentHeight = ftf.getHeight();

        // Pick a new font size so it will not be larger than the height of label.
        int fontSizeToUse = Math.min(newFontSize, componentHeight);
        System.out.println("Font size to use is "+ String.valueOf(fontSizeToUse));
        return fontSizeToUse;
    }
}

