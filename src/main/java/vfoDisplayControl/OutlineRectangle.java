/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import vfoDisplayControl.Geometry;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

/**
 * Represents the outline of a rectangle.
 * @author Coz
 */
    
public class OutlineRectangle extends Geometry {
    public OutlineRectangle(Rectangle bounds, Color outlineColor) {
        super(bounds, outlineColor, false, 4, "Outline Rectangle");
    }
    @Override
    public void draw(Graphics g) {
        Rectangle scaledBounds = getScaledBounds();
        g.setColor(usedColor);
        g.drawRect(scaledBounds.x, scaledBounds.y, scaledBounds.width, scaledBounds.height);            
    }
}

