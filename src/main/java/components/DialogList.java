/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JList;
import javax.swing.SwingConstants;

/**
 *
 * @author Coz copied from oracle example.
 */
public class DialogList extends JList {
    
    public DialogList(String[] data) {
        super(data);
    }
        //Subclass JList to workaround bug 4832765, which can cause the
        //scroll pane to not let the user easily scroll up to the beginning
        //of the list.  An alternative would be to set the unitIncrement
        //of the JScrollBar to a fixed value. You wouldn't get the nice
        //aligned scrolling, but it should work.
    public int getScrollableUnitIncrement(Rectangle visibleRect,
                                              int orientation,
                                              int direction) {
        int row;
        if (orientation == SwingConstants.VERTICAL &&
              direction < 0 && (row = getFirstVisibleIndex()) != -1) {
            Rectangle r = getCellBounds(row, row);
            if ((r.y == visibleRect.y) && (row != 0))  {
                Point loc = r.getLocation();
                loc.y--;
                int prevIndex = locationToIndex(loc);
                Rectangle prevR = getCellBounds(prevIndex, prevIndex);

                if (prevR == null || prevR.y >= r.y) {
                    return 0;
                }
                return prevR.height;
            }
        }
        return super.getScrollableUnitIncrement(
                        visibleRect, orientation, direction);
    }
}    
 
