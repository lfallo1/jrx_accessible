package vfoDisplayControl;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */



import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.plaf.InternalFrameUI;
import javax.swing.plaf.basic.BasicInternalFrameUI;

/**
 * GroupBox is a borderless JInternalFrame without a title which makes the perfect
 * voiceOver group.  VoiceOver announces it as a "Group" and gives appropriate
 * instructions on how to enter and exit the group right out of the box.  Just
 * remember that the JInternalFrame comes with all the goodies and draws by
 * default on the contentPane.
 * 
 * Generally,
 * you add <code>JInternalFrame</code>s to a <code>JDesktopPane</code>. The UI
 * delegates the look-and-feel-specific actions to the
 * <code>DesktopManager</code>
 * object maintained by the <code>JDesktopPane</code>.
 * <p>
 * @author Coz
 */
public class GroupBox extends JInternalFrame {
    BasicInternalFrameUI basicFrameUI;
    
    public GroupBox() {
        BasicInternalFrameUI basicFrameUI = (BasicInternalFrameUI)this.getUI();
        basicFrameUI.setNorthPane(null);
        this.setBorder(null);
        
    }
}
