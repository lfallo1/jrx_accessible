/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;
 
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
 
/*
 * ListDialog.java was meant to be used by programs such as
 * ListDialogRunner.  It required no additional files.  It has been modified
 * extensively so it probably will NOT work in the example programs.
 */
 
/**
 * Modal dialog for selection of one string from a long list.  
 * See ListDialogRunner.java for an example of using ListDialog.
 * The basics:
 * <pre>
    String[] choices = {"A", "long", "array", "of", "strings"};
    String selectedName = ListDialog.showDialog(
                                ControllingFrame,
                                locatorComponent,
                                "A description of the list:",
                                "Dialog Title",
                                initialIndex);
 * </pre>
 */
public class ListDialog extends JDialog {
    protected String value = "";
    protected JList list;
    protected Component buttonComp;
    protected String title;
    protected Integer initialIndex;
    protected String name;
    protected String labelTxt;
    private JScrollPane listScroller;
    
 
    /**
    * The first Component argument
     * determines which frame the dialog depends on; it should be
     * a component in the dialog's controlling frame. The second
     * Component argument should be null if you want the dialog
     * to come up with its left corner in the center of the screen;
     * otherwise, it should be the component on top of which the
     * dialog should appear.
     */ 
    public ListDialog( Component frame,
                       Component locationComp,// The ListButton
                       String labelText,
                       String aTitle,
                       Integer initIndex,
                       String[] listData) {
        
        super((JFrame)frame, aTitle, true);
        
        buttonComp = locationComp;
        title = aTitle;
        initialIndex = initIndex;
        labelTxt = labelText;
        //Create and initialize the buttons.
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener((RWListButton)locationComp);
        //
        final JButton setButton = new JButton("Set");
        setButton.setActionCommand("Set");
        setButton.addActionListener((RWListButton)locationComp);
        getRootPane().setDefaultButton(setButton);
        
        list = (JList) new DialogList(listData);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        list.setVisibleRowCount(-1);  // was -1
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    setButton.doClick(); //emulate button click
                }
            }
        });

        listScroller = new JScrollPane(list);        
        listScroller.setPreferredSize(new Dimension(250, 280));
        listScroller.setAlignmentX(LEFT_ALIGNMENT);
 
        //Create a container so that we can add a title around
        //the scroll pane.  Can't add a title directly to the
        //scroll pane because its background would be white.
        //Lay out the label and scroll pane from top to bottom.
        JPanel listPane = new JPanel();
        listPane.setLayout(new BoxLayout(listPane, BoxLayout.PAGE_AXIS));
        JLabel label = new JLabel(labelTxt);
        label.setLabelFor(list);
        listPane.add(label);
        listPane.add(Box.createRigidArea(new Dimension(0,5)));
        listPane.add(listScroller);
        listPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
 
        //Lay out the buttons from left to right.
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
        buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        buttonPane.add(Box.createHorizontalGlue());
        buttonPane.add(cancelButton);
        buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
        buttonPane.add(setButton);
 
        //Put everything together, using the content pane's BorderLayout.
        Container contentPane = getContentPane();
        contentPane.add(listPane, BorderLayout.CENTER);
        contentPane.add(buttonPane, BorderLayout.PAGE_END);

        value = ((String)listData[initialIndex]);
        list.setSelectedIndex(initialIndex);
        list.setSelectedValue(value, true);
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent evt) {
                if (evt.getValueIsAdjusting())
                    return;
                value = (String)(list.getSelectedValue());
                //System.out.println("ListDialog:ListSelectionListener() selected " + value);
                int index = evt.getFirstIndex();
                list.ensureIndexIsVisible(index);
            }
        });

        pack();
    }
    
    public void setNewData(String[] newData) {
        list.setListData(newData);
        list.setSelectedIndex(initialIndex);
        value = ((String)newData[initialIndex]);
        list.setSelectedValue(value, true);         
    }
    
    /**
     * Show the dialog and return selection.
     */
    public String showDialog() {
        int index = ((RWListButton)buttonComp).getSelectedIndex();
        list.setSelectedIndex(index);
        setLocationRelativeTo(buttonComp);        
        setVisible(true);
        return value;
    }      
}
