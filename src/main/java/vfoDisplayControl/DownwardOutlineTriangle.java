/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.Rectangle;

/**
 * Represents outline of bilateral triangle with point at the bottom 
 * (arrow points down).
 * 
 * @author Coz
 */
public class DownwardOutlineTriangle extends Geometry {
    int[] x = new int[3];
    int[] y = new int[3];
    public DownwardOutlineTriangle(Rectangle bounds, Color outlineColor) {
        super(bounds, outlineColor, false, 3, "Downward Outline Triangle");
    }
    @Override
    public void draw(Graphics g) {
        g.setColor(usedColor);
        Rectangle scaledBounds = getScaledBounds();
        // Equilateral triangle with point at the bottom.
        x[0] = scaledBounds.x + scaledBounds.width/2; // rock-bottom
        y[0] = scaledBounds.y + scaledBounds.height;
        x[1] = scaledBounds.x + scaledBounds.width;
        y[1] = scaledBounds.y;
        x[2] = scaledBounds.x;
        y[2] = y[1];
        Polygon p = new Polygon(x,y,3);
        g.drawPolygon(p);
    } 

}

