/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

/**
 * Represents a filled rectangle.
 * @author Coz
 */
public class FilledRectangle extends Geometry {
   
    public FilledRectangle(Rectangle bounds, Color fillColor) {
        super(bounds, fillColor, true, 4, "Filled Rectangle");
        dims = new Dimension(bounds.width, bounds.height);
    }
    @Override
    public void draw(Graphics g) {
        Rectangle scaledBounds = getScaledBounds();
        g.setColor(usedColor);
        g.fillRect(scaledBounds.x, scaledBounds.y, 
                scaledBounds.width, scaledBounds.height);            
    }
} 
