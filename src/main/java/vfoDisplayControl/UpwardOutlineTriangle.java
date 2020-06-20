/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import vfoDisplayControl.Geometry;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.Rectangle;

/**
 * Represents outline of bilateral triangle with point at the top
 * (arrow points up).
 *
 * @author Coz
 */
public class UpwardOutlineTriangle extends Geometry {
    int[] x = new int[3];
    int[] y = new int[3];

    public UpwardOutlineTriangle(Rectangle bounds, Color outlineColor) {
        super(bounds, outlineColor, false, 3, "Upward Outline Triangle");
    }
    @Override
    public void draw(Graphics g) {
        g.setColor(usedColor);
        Rectangle scaledBounds = getScaledBounds();
        // Equilateral triangle with point at the top.
        x[0] = scaledBounds.x+scaledBounds.width/2; // apex
        y[0] = scaledBounds.y;
        x[1] = scaledBounds.x+scaledBounds.width;
        y[1] = scaledBounds.y+scaledBounds.height;
        x[2] = scaledBounds.x;
        y[2] = y[1];
        Polygon p = new Polygon(x,y,sides);
        g.drawPolygon(p);          
    }
}
