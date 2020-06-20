/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import vfoDisplayControl.Geometry;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;

/**
 * Polymorphic storage for component shapes that extend class Geometry and 
 * override the draw() method.
 * 
 * @author Coz
 */
public class GeometryCollection {
    protected Dimension dims;
    private ArrayList<Geometry> shapes;

    public GeometryCollection() {
        this.shapes = new ArrayList<Geometry>();
    }
    
    public void clear() {
        shapes.clear();
    }

    public void addGeometry( Geometry geo) {
        shapes.add(geo);
    }

    public void setDims(Dimension dim) {
        dims = dim;
    }

    /**
     * Draw each the members of the ArrayList in order.
     * @param g is Graphics g from Component to be drawn on.
    */
    public void draw(Graphics g) {
         for(Geometry geo:shapes){
            geo.setDims(dims);
            geo.draw(g);         
        }
    }
}

