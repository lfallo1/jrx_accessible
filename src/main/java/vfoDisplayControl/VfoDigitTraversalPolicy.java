/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.util.Vector;


/**
 * Implements a focus traversal policy for the VfoDisplayPanel frame where each
 * digit may be traversed in order of decades which makes more sense when it
 * is right to left.
 * 
* This code stolen from Oracle example FocusTraversalDemo.java
*/       
public class VfoDigitTraversalPolicy extends FocusTraversalPolicy
{
    Vector<Component> order;

    public VfoDigitTraversalPolicy(Vector<Component> order) {
        this.order = new Vector<Component>(order.size());
        this.order.addAll(order);
    }
    public Component getComponentAfter(Container focusCycleRoot,
                                       Component aComponent)
    {
        int idx = (order.indexOf(aComponent) + 1) % order.size();
        return order.get(idx);
    }

    public Component getComponentBefore(Container focusCycleRoot,
                                        Component aComponent)
    {
        int idx = order.indexOf(aComponent) - 1;
        if (idx < 0) {
            idx = order.size() - 1;
        }
        return order.get(idx);
    }

    public Component getDefaultComponent(Container focusCycleRoot) {
        return order.get(0);
    }

    public Component getLastComponent(Container focusCycleRoot) {
        return order.lastElement();
    }

    public Component getFirstComponent(Container focusCycleRoot) {
        return order.get(0);
    }
}         


